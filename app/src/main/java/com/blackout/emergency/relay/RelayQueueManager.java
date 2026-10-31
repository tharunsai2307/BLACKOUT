package com.blackout.emergency.relay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.database.BlackoutDatabase;
import com.blackout.emergency.database.EmergencyMessageDao;
import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.RelayHistoryDao;
import com.blackout.emergency.database.RelayHistoryEntity;
import com.blackout.emergency.database.RelayQueueDao;
import com.blackout.emergency.database.RelayQueueEntity;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the persistent store-carry-and-forward relay queue and delivery history.
 * Executes Room operations asynchronously off the main thread.
 */
public class RelayQueueManager {

    private static volatile RelayQueueManager instance;
    private final RelayQueueDao queueDao;
    private final RelayHistoryDao historyDao;
    private final EmergencyMessageDao messageDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface Callback<T> {
        void onComplete(T result);
        default void onError(Exception e) {}
    }

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                return new Handler(looper);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void postToMain(Runnable r) {
        if (r == null) return;
        if (mainHandler != null) {
            mainHandler.post(r);
        } else {
            r.run();
        }
    }

    private RelayQueueManager(Context context) {
        BlackoutDatabase db = BlackoutDatabase.getInstance(context.getApplicationContext());
        this.queueDao = db.relayQueueDao();
        this.historyDao = db.relayHistoryDao();
        this.messageDao = db.emergencyMessageDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = createSafeMainHandler();
    }

    public RelayQueueManager(RelayQueueDao queueDao, RelayHistoryDao historyDao,
                             EmergencyMessageDao messageDao, ExecutorService executor) {
        this.queueDao = queueDao;
        this.historyDao = historyDao;
        this.messageDao = messageDao;
        this.executor = executor;
        this.mainHandler = createSafeMainHandler();
    }

    public static synchronized RelayQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new RelayQueueManager(context);
        }
        return instance;
    }

    /**
     * Enqueues an SOS message into the persistent relay queue if eligible.
     * Prevents duplicate queue records.
     */
    public void enqueueForRelay(@NonNull final EmergencyMessage message, @Nullable final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                if (!RelayPolicy.isEligibleForRelay(message)) {
                    if (callback != null) {
                        postToMain(() -> callback.onComplete(false));
                    }
                    return;
                }

                // Check if already in queue
                RelayQueueEntity existing = queueDao.getByMessageId(message.getMessageId());
                if (existing != null) {
                    if (callback != null) {
                        postToMain(() -> callback.onComplete(false));
                    }
                    return;
                }

                long now = System.currentTimeMillis();
                RelayQueueEntity entity = new RelayQueueEntity(
                        message.getMessageId(),
                        Constants.RELAY_STATUS_PENDING,
                        now,
                        now, // Ready immediately
                        0,
                        0L,
                        null,
                        message.getPriority()
                );
                queueDao.insert(entity);

                if (callback != null) {
                    postToMain(() -> callback.onComplete(true));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(false);
                    });
                }
            }
        });
    }

    /**
     * Retrieves eligible queued emergency messages prioritized and filtered against a peer's known inventory.
     */
    public void getEligibleMessagesForPeer(@NonNull final String peerDeviceId,
                                          @NonNull final List<String> peerKnownIds,
                                          final int limit,
                                          @NonNull final Callback<List<EmergencyMessage>> callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                List<RelayQueueEntity> queueItems = queueDao.getEligibleQueueItems(now, limit * 2);
                List<EmergencyMessage> eligible = new ArrayList<>();

                for (RelayQueueEntity item : queueItems) {
                    if (eligible.size() >= limit) {
                        break;
                    }

                    String msgId = item.getMessageId();
                    // 1. Peer already reported having this message
                    if (peerKnownIds.contains(msgId)) {
                        continue;
                    }

                    // 2. We already successfully delivered this message to this specific peer
                    int delivered = historyDao.countDeliveredToPeer(msgId, peerDeviceId);
                    if (delivered > 0) {
                        continue;
                    }

                    // 3. Fetch full emergency message from Room
                    EmergencyMessageEntity msgEntity = messageDao.getMessageById(msgId);
                    if (msgEntity == null) {
                        // Dead queue item, remove
                        queueDao.deleteByMessageId(msgId);
                        continue;
                    }

                    EmergencyMessage model = msgEntity.toModel();
                    // 4. Double check eligibility (hop count, expiration)
                    if (!RelayPolicy.isEligibleForRelay(model)) {
                        String status = (model.getExpiresAt() != null && model.getExpiresAt() <= now)
                                ? Constants.RELAY_STATUS_EXPIRED
                                : Constants.RELAY_STATUS_MAX_HOPS_REACHED;
                        queueDao.updateStatus(msgId, status, now);
                        messageDao.updateStatus(msgId, status);
                        continue;
                    }

                    eligible.add(model);
                }

                postToMain(() -> callback.onComplete(eligible));
            } catch (Exception e) {
                postToMain(() -> {
                    callback.onError(e);
                    callback.onComplete(new ArrayList<>());
                });
            }
        });
    }

    /**
     * Records successful peer acknowledgement of an SOS message.
     * Updates queue status, logs transfer in relay history, and updates message status.
     */
    public void recordDeliverySuccess(@NonNull final String messageId,
                                      @NonNull final String localDeviceId,
                                      @NonNull final String peerDeviceId,
                                      final int hopNumber,
                                      @Nullable final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();

                // 1. Record in relay_history
                RelayHistoryEntity history = new RelayHistoryEntity(
                        messageId,
                        localDeviceId,
                        peerDeviceId,
                        hopNumber,
                        Constants.STATUS_RELAYED,
                        now
                );
                historyDao.insert(history);

                // 2. Update relay_queue item
                queueDao.updateStatus(messageId, Constants.RELAY_STATUS_RELAYED_TO_PEER, now);

                // 3. Update emergency_messages record
                messageDao.updateStatus(messageId, Constants.STATUS_TRANSFERRED);

                if (callback != null) {
                    postToMain(() -> callback.onComplete(true));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(false);
                    });
                }
            }
        });
    }

    /**
     * Records a failed delivery attempt and schedules retry with bounded exponential backoff.
     */
    public void recordDeliveryFailure(@NonNull final String messageId,
                                      @Nullable final String errorReason,
                                      @Nullable final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                RelayQueueEntity item = queueDao.getByMessageId(messageId);
                if (item != null) {
                    int attempts = item.getAttemptCount() + 1;
                    if (attempts >= RelayPolicy.MAX_RETRY_ATTEMPTS) {
                        // Max retries reached
                        queueDao.updateRetry(messageId, Constants.RELAY_STATUS_FAILED, attempts, 0L, now, errorReason);
                        messageDao.updateStatus(messageId, Constants.STATUS_TRANSFER_FAILED);
                    } else {
                        long backoff = RelayPolicy.calculateBackoffMillis(attempts);
                        long nextAttempt = now + backoff;
                        queueDao.updateRetry(messageId, Constants.RELAY_STATUS_RETRY_WAIT, attempts, nextAttempt, now, errorReason);
                    }
                }

                if (callback != null) {
                    postToMain(() -> callback.onComplete(true));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(false);
                    });
                }
            }
        });
    }

    /**
     * Retrieves all known message IDs currently stored in local database to construct an inventory.
     */
    public void getLocalInventoryIds(@NonNull final Callback<List<String>> callback) {
        executor.execute(() -> {
            try {
                List<EmergencyMessageEntity> all = messageDao.getAllMessages();
                List<String> ids = new ArrayList<>();
                int max = Math.min(all.size(), RelayPolicy.MAX_INVENTORY_SIZE);
                for (int i = 0; i < max; i++) {
                    ids.add(all.get(i).getMessageId());
                }
                postToMain(() -> callback.onComplete(ids));
            } catch (Exception e) {
                postToMain(() -> {
                    callback.onError(e);
                    callback.onComplete(Collections.emptyList());
                });
            }
        });
    }

    /**
     * Gets count of pending relay messages.
     */
    public void getPendingRelayCount(@NonNull final Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = queueDao.getPendingRelayCount();
                postToMain(() -> callback.onComplete(count));
            } catch (Exception e) {
                postToMain(() -> {
                    callback.onError(e);
                    callback.onComplete(0);
                });
            }
        });
    }

    /**
     * Retrieves recent relay history.
     */
    public void getRecentHistory(final int limit, @NonNull final Callback<List<RelayHistoryEntity>> callback) {
        executor.execute(() -> {
            try {
                List<RelayHistoryEntity> list = historyDao.getRecentHistory(limit);
                postToMain(() -> callback.onComplete(list));
            } catch (Exception e) {
                postToMain(() -> {
                    callback.onError(e);
                    callback.onComplete(Collections.emptyList());
                });
            }
        });
    }
}
