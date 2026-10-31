package com.blackout.emergency.gateway;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.blackout.emergency.database.BlackoutDatabase;
import com.blackout.emergency.database.EmergencyMessageDao;
import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.database.PendingUploadDao;
import com.blackout.emergency.database.PendingUploadEntity;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the offline-to-online upload queue of emergency SOS messages.
 * When the device has internet access (acting as an Internet Gateway),
 * this manager batches eligible pending messages, securely transmits them
 * to the Supabase backend, and records the upload result.
 */
public class PendingUploadManager {

    private static final String TAG = "PendingUploadManager";
    private static final int BATCH_SIZE = 10;
    private static final long INITIAL_RETRY_DELAY_MS = 5000L;
    private static final long MAX_RETRY_DELAY_MS = 300000L; // 5 minutes

    public interface GatewaySyncListener {
        void onSyncStarted();
        void onSyncProgress(int current, int total);
        void onSyncCompleted(int uploadedCount, int failedCount, int pendingRemaining);
    }

    private static volatile PendingUploadManager instance;
    private final Context context;
    private final PendingUploadDao uploadDao;
    private final EmergencyMessageDao messageDao;
    private final BackendApiClient apiClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private final Set<GatewaySyncListener> listeners = Collections.synchronizedSet(new HashSet<>());

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

    private PendingUploadManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        BlackoutDatabase db = BlackoutDatabase.getInstance(this.context);
        this.uploadDao = db.pendingUploadDao();
        this.messageDao = db.emergencyMessageDao();
        this.apiClient = new BackendApiClient(this.context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = createSafeMainHandler();
    }

    public static synchronized PendingUploadManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new PendingUploadManager(context);
        }
        return instance;
    }

    @VisibleForTesting
    public PendingUploadManager(@Nullable Context context,
                                @NonNull PendingUploadDao uploadDao,
                                @NonNull EmergencyMessageDao messageDao,
                                @NonNull BackendApiClient apiClient,
                                @NonNull ExecutorService executor) {
        this.context = context;
        this.uploadDao = uploadDao;
        this.messageDao = messageDao;
        this.apiClient = apiClient;
        this.executor = executor;
        this.mainHandler = createSafeMainHandler();
    }

    public void registerSyncListener(@NonNull GatewaySyncListener listener) {
        listeners.add(listener);
    }

    public void unregisterSyncListener(@NonNull GatewaySyncListener listener) {
        listeners.remove(listener);
    }

    public BackendApiClient getApiClient() {
        return apiClient;
    }

    public boolean isSyncing() {
        return isSyncing.get();
    }

    private String getGatewayDeviceId() {
        if (context == null) return "GATEWAY-TEST";
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_NODE_CALLSIGN, Constants.DEFAULT_NODE_CALLSIGN);
    }

    /**
     * Enqueues an emergency message into the pending upload table for Internet Gateway processing.
     */
    public void enqueueForUpload(@NonNull final EmergencyMessage message, @Nullable final EmergencyRepository.Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                PendingUploadEntity existing = uploadDao.getByMessageId(message.getMessageId());
                if (existing == null) {
                    long now = System.currentTimeMillis();
                    PendingUploadEntity entity = new PendingUploadEntity(
                            message.getMessageId(),
                            Constants.UPLOAD_STATUS_PENDING,
                            getGatewayDeviceId(),
                            0,
                            now, // eligible immediately
                            0L,
                            0L,
                            null,
                            null
                    );
                    uploadDao.insert(entity);
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
     * Attempts to flush and upload eligible pending messages to the Supabase central backend.
     * Guaranteed to process off the main thread with bounded concurrency.
     */
    public void flushPendingUploads(@Nullable final EmergencyRepository.Callback<Integer> callback) {
        if (!isSyncing.compareAndSet(false, true)) {
            // Already syncing, avoid duplicate concurrent workers
            if (callback != null) {
                postToMain(() -> callback.onComplete(0));
            }
            return;
        }

        notifySyncStarted();

        executor.execute(() -> {
            int totalUploaded = 0;
            int totalFailed = 0;
            try {
                long now = System.currentTimeMillis();
                List<PendingUploadEntity> batch = uploadDao.getEligibleUploads(now, BATCH_SIZE);

                for (int i = 0; i < batch.size(); i++) {
                    PendingUploadEntity item = batch.get(i);
                    notifySyncProgress(i + 1, batch.size());

                    EmergencyMessageEntity msgEntity = messageDao.getMessageById(item.getMessageId());
                    if (msgEntity == null) {
                        // Message no longer exists in Room database
                        uploadDao.deleteByMessageId(item.getMessageId());
                        continue;
                    }

                    EmergencyMessage model = msgEntity.toModel();
                    long currentTime = System.currentTimeMillis();

                    // Check if expired
                    if (model.getExpiresAt() != null && model.getExpiresAt() <= currentTime) {
                        uploadDao.markAccepted(model.getMessageId(), Constants.UPLOAD_STATUS_EXPIRED, currentTime, null);
                        continue;
                    }

                    // Attempt upload
                    BackendApiClient.UploadResult result = apiClient.uploadAlert(model, getGatewayDeviceId());

                    if (result.isSuccess()) {
                        totalUploaded++;
                        String acceptedStatus = result.isDuplicate()
                                ? Constants.UPLOAD_STATUS_DUPLICATE_ACCEPTED
                                : Constants.UPLOAD_STATUS_BACKEND_ACCEPTED;
                        uploadDao.markAccepted(model.getMessageId(), acceptedStatus, currentTime, result.getBackendReference());
                    } else if (result.isRetryable()) {
                        totalFailed++;
                        int nextAttempt = item.getAttemptCount() + 1;
                        long backoff = Math.min(MAX_RETRY_DELAY_MS, INITIAL_RETRY_DELAY_MS * (1L << Math.min(nextAttempt, 6)));
                        uploadDao.updateRetry(
                                model.getMessageId(),
                                Constants.UPLOAD_STATUS_RETRY_WAIT,
                                nextAttempt,
                                currentTime + backoff,
                                currentTime,
                                result.getErrorMessage()
                        );
                    } else {
                        // Non-retryable rejection (400, 409 conflict, etc.)
                        totalFailed++;
                        int nextAttempt = item.getAttemptCount() + 1;
                        uploadDao.updateRetry(
                                model.getMessageId(),
                                Constants.UPLOAD_STATUS_REJECTED,
                                nextAttempt,
                                Long.MAX_VALUE, // Never retry rejected payloads
                                currentTime,
                                result.getErrorMessage()
                        );
                    }
                }
            } catch (Exception e) {
                // Log or capture error
            } finally {
                isSyncing.set(false);
                int pendingRemaining = uploadDao.getPendingUploadCount();
                notifySyncCompleted(totalUploaded, totalFailed, pendingRemaining);
                final int uploadedResult = totalUploaded;
                if (callback != null) {
                    postToMain(() -> callback.onComplete(uploadedResult));
                }
            }
        });
    }

    /**
     * Gets count of pending uploads remaining.
     */
    public void getPendingCount(@NonNull final EmergencyRepository.Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = uploadDao.getPendingUploadCount();
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
     * Gets all upload records for audit/inspection.
     */
    public void getAllUploadRecords(@NonNull final EmergencyRepository.Callback<List<PendingUploadEntity>> callback) {
        executor.execute(() -> {
            try {
                List<PendingUploadEntity> list = uploadDao.getAllUploads();
                postToMain(() -> callback.onComplete(list));
            } catch (Exception e) {
                postToMain(() -> {
                    callback.onError(e);
                    callback.onComplete(Collections.emptyList());
                });
            }
        });
    }

    private void notifySyncStarted() {
        postToMain(() -> {
            synchronized (listeners) {
                for (GatewaySyncListener listener : listeners) {
                    if (listener != null) listener.onSyncStarted();
                }
            }
        });
    }

    private void notifySyncProgress(int current, int total) {
        postToMain(() -> {
            synchronized (listeners) {
                for (GatewaySyncListener listener : listeners) {
                    if (listener != null) listener.onSyncProgress(current, total);
                }
            }
        });
    }

    private void notifySyncCompleted(int uploaded, int failed, int pending) {
        postToMain(() -> {
            synchronized (listeners) {
                for (GatewaySyncListener listener : listeners) {
                    if (listener != null) listener.onSyncCompleted(uploaded, failed, pending);
                }
            }
        });
    }
}
