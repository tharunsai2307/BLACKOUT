package com.blackout.emergency.warnings.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.blackout.emergency.warnings.database.WarningRepository;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import com.blackout.emergency.warnings.notifications.WarningNotificationManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Background WorkManager worker responsible for synchronizing digitally signed
 * public emergency warnings and trusted key updates when internet connectivity is available (Phase 7).
 */
public class WarningSyncWorker extends Worker {

    public static final String WORK_NAME_PERIODIC = "work_warning_sync_periodic";
    public static final String WORK_NAME_ONETIME = "work_warning_sync_onetime";

    private final WarningApiClient apiClient;
    private final WarningRepository repository;
    private final WarningNotificationManager notificationManager;

    public WarningSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.apiClient = new WarningApiClient(context);
        this.repository = WarningRepository.getInstance(context);
        this.notificationManager = WarningNotificationManager.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // 1. Fetch Key Registry Updates
            try {
                List<TrustedSigningKey> updatedKeys = apiClient.fetchTrustedKeys();
                if (!updatedKeys.isEmpty()) {
                    repository.persistKeys(updatedKeys);
                }
            } catch (Exception e) {
                // Non-fatal if root key is already present
            }

            // 2. Fetch Active Published Warnings
            List<PublicWarning> activeWarnings = apiClient.fetchActiveWarnings();
            int newOrUpdatedCount = 0;

            if (!activeWarnings.isEmpty()) {
                final CountDownLatch latch = new CountDownLatch(activeWarnings.size());
                final int[] successCounter = new int[1];

                for (PublicWarning warning : activeWarnings) {
                    repository.processAndStoreWarning(warning, (result, w, msg) -> {
                        if (result == WarningRepository.WarningProcessResult.ACCEPTED_NEW
                                || result == WarningRepository.WarningProcessResult.ACCEPTED_REVISION_UPDATE) {
                            successCounter[0]++;
                            // Dispatch high-priority emergency notification for authentic verified warnings
                            if (w != null && w.isAuthenticAndVerified()) {
                                notificationManager.notifyWarning(w);
                            }
                        }
                        latch.countDown();
                    });
                }

                latch.await(30, TimeUnit.SECONDS);
                newOrUpdatedCount = successCounter[0];
            }

            repository.updateSyncState("LAST_SYNC_WARNINGS", activeWarnings.size(), "SUCCESS", null);
            return Result.success();

        } catch (Exception e) {
            repository.updateSyncState("LAST_SYNC_WARNINGS", 0, "FAILED", e.getMessage());
            return Result.retry();
        }
    }

    /**
     * Enqueues an immediate one-time sync request when internet connectivity is detected.
     */
    public static void enqueueOneTimeSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WarningSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }

    /**
     * Schedules periodic background sync (e.g. every 15 minutes when connected).
     */
    public static void schedulePeriodicSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
                WarningSyncWorker.class,
                15, TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
        );
    }
}

