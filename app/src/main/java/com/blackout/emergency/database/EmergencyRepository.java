package com.blackout.emergency.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.gateway.PendingUploadManager;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayQueueManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository layer managing offline storage and retrieval of Emergency SOS messages.
 * Executes Room database operations asynchronously off the main thread and posts
 * results back to the UI thread via a Handler.
 * Automatically enqueues newly created and received SOS messages for store-carry-and-forward relay
 * and for Internet Gateway cloud synchronization when internet connectivity becomes available.
 */
public class EmergencyRepository {

    private static volatile EmergencyRepository instance;
    private final EmergencyMessageDao messageDao;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private RelayQueueManager relayQueueManager;
    private PendingUploadManager pendingUploadManager;

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

    private EmergencyRepository(Context context) {
        BlackoutDatabase db = BlackoutDatabase.getInstance(context.getApplicationContext());
        this.messageDao = db.emergencyMessageDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = createSafeMainHandler();
        try {
            this.relayQueueManager = RelayQueueManager.getInstance(context.getApplicationContext());
        } catch (Exception ignored) {}
        try {
            this.pendingUploadManager = PendingUploadManager.getInstance(context.getApplicationContext());
        } catch (Exception ignored) {}
    }

    public static synchronized EmergencyRepository getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyRepository(context);
        }
        return instance;
    }

    /**
     * Testing constructor allowing injection of DAO, Executor, and RelayQueueManager.
     */
    public EmergencyRepository(EmergencyMessageDao messageDao, ExecutorService executor) {
        this(messageDao, executor, null, null);
    }

    public EmergencyRepository(EmergencyMessageDao messageDao, ExecutorService executor, @Nullable RelayQueueManager relayQueueManager) {
        this(messageDao, executor, relayQueueManager, null);
    }

    public EmergencyRepository(EmergencyMessageDao messageDao, ExecutorService executor,
                               @Nullable RelayQueueManager relayQueueManager,
                               @Nullable PendingUploadManager pendingUploadManager) {
        this.messageDao = messageDao;
        this.executor = executor;
        this.mainHandler = createSafeMainHandler();
        this.relayQueueManager = relayQueueManager;
        this.pendingUploadManager = pendingUploadManager;
    }

    public void setRelayQueueManager(RelayQueueManager relayQueueManager) {
        this.relayQueueManager = relayQueueManager;
    }

    public void setPendingUploadManager(PendingUploadManager pendingUploadManager) {
        this.pendingUploadManager = pendingUploadManager;
    }

    public PendingUploadManager getPendingUploadManager() {
        return pendingUploadManager;
    }

    /**
     * Inserts an emergency message asynchronously and enqueues it for relay forwarding
     * and internet gateway synchronization.
     */
    public void insertMessage(@NonNull final EmergencyMessage message, final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                EmergencyMessageEntity entity = EmergencyMessageEntity.fromModel(message);
                messageDao.insert(entity);

                // Enqueue in relay pipeline
                if (relayQueueManager != null) {
                    relayQueueManager.enqueueForRelay(message, null);
                }

                // Enqueue in cloud gateway upload pipeline (Phase 5)
                if (pendingUploadManager != null) {
                    pendingUploadManager.enqueueForUpload(message, null);
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
     * Retrieves all saved emergency messages asynchronously (newest first).
     */
    public void getAllMessages(final Callback<List<EmergencyMessage>> callback) {
        executor.execute(() -> {
            try {
                List<EmergencyMessageEntity> entities = messageDao.getAllMessages();
                List<EmergencyMessage> models = new ArrayList<>();
                for (EmergencyMessageEntity entity : entities) {
                    models.add(entity.toModel());
                }
                if (callback != null) {
                    postToMain(() -> callback.onComplete(models));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(new ArrayList<>());
                    });
                }
            }
        });
    }

    /**
     * Retrieves a single message by ID.
     */
    public void getMessageById(final String messageId, final Callback<EmergencyMessage> callback) {
        executor.execute(() -> {
            try {
                EmergencyMessageEntity entity = messageDao.getMessageById(messageId);
                EmergencyMessage model = entity != null ? entity.toModel() : null;
                if (callback != null) {
                    postToMain(() -> callback.onComplete(model));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(null);
                    });
                }
            }
        });
    }

    /**
     * Retrieves messages filtered by category.
     */
    public void getMessagesByCategory(final String category, final Callback<List<EmergencyMessage>> callback) {
        executor.execute(() -> {
            try {
                List<EmergencyMessageEntity> entities = messageDao.getMessagesByCategory(category);
                List<EmergencyMessage> models = new ArrayList<>();
                for (EmergencyMessageEntity entity : entities) {
                    models.add(entity.toModel());
                }
                if (callback != null) {
                    postToMain(() -> callback.onComplete(models));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(new ArrayList<>());
                    });
                }
            }
        });
    }

    /**
     * Deletes a message by ID.
     */
    public void deleteMessage(final String messageId, final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                messageDao.deleteById(messageId);
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
     * Gets total message count.
     */
    public void getMessageCount(final Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = messageDao.getMessageCount();
                if (callback != null) {
                    postToMain(() -> callback.onComplete(count));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(0);
                    });
                }
            }
        });
    }

    /**
     * Updates the status of an existing message.
     */
    public void updateMessageStatus(final String messageId, final String newStatus, final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                messageDao.updateStatus(messageId, newStatus);
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
     * Retrieves all messages created on this device eligible for sending/transferring.
     */
    public void getOutgoingMessages(final Callback<List<EmergencyMessage>> callback) {
        executor.execute(() -> {
            try {
                List<EmergencyMessageEntity> entities = messageDao.getOutgoingMessages();
                List<EmergencyMessage> models = new ArrayList<>();
                for (EmergencyMessageEntity entity : entities) {
                    models.add(entity.toModel());
                }
                if (callback != null) {
                    postToMain(() -> callback.onComplete(models));
                }
            } catch (Exception e) {
                if (callback != null) {
                    postToMain(() -> {
                        callback.onError(e);
                        callback.onComplete(new ArrayList<>());
                    });
                }
            }
        });
    }

    /**
     * Safely inserts an incoming SOS message received from a nearby peer.
     * Prevents duplicate records by checking messageId. If already present, does NOT overwrite.
     * Enqueues eligible incoming messages for further multi-hop relay.
     */
    public void insertReceivedMessage(@NonNull final EmergencyMessage message, final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                // Duplicate check
                EmergencyMessageEntity existing = messageDao.getMessageById(message.getMessageId());
                if (existing != null) {
                    // Duplicate already recorded, do not overwrite
                    if (callback != null) {
                        postToMain(() -> callback.onComplete(false));
                    }
                    return;
                }

                message.setStatus(com.blackout.emergency.utils.Constants.STATUS_RECEIVED);
                EmergencyMessageEntity entity = EmergencyMessageEntity.fromModel(message);
                messageDao.insert(entity);

                // Enqueue received SOS for further relay across future encounters
                if (relayQueueManager != null) {
                    relayQueueManager.enqueueForRelay(message, null);
                }

                // Enqueue received SOS for cloud gateway upload when internet is available (Phase 5)
                if (pendingUploadManager != null) {
                    pendingUploadManager.enqueueForUpload(message, null);
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
     * Deletes all messages from the database.
     */
    public void deleteAllMessages(final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                messageDao.deleteAll();
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
}
