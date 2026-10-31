package com.blackout.emergency.warnings.database;

import android.content.Context;
import androidx.annotation.NonNull;
import com.blackout.emergency.database.BlackoutDatabase;
import com.blackout.emergency.database.PublicWarningDao;
import com.blackout.emergency.database.PublicWarningEntity;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages expiration lifecycle of locally stored warnings (Phase 7).
 * Ensures expired warnings are strictly prevented from active display or unauthorized mesh relay.
 */
public class WarningExpiryManager {

    private static volatile WarningExpiryManager instance;
    private final PublicWarningDao warningDao;
    private final ExecutorService executor;

    public WarningExpiryManager(@NonNull Context context) {
        this.warningDao = BlackoutDatabase.getInstance(context).publicWarningDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized WarningExpiryManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new WarningExpiryManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Scans and updates expired warnings in background.
     */
    public void sweepExpiredWarnings(final Runnable onComplete) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                List<PublicWarningEntity> all = warningDao.getAllWarnings();
                for (PublicWarningEntity entity : all) {
                    if (now > entity.getExpiresAt() && !PublicWarning.STATUS_EXPIRED.equals(entity.getStatus())) {
                        entity.setStatus(PublicWarning.STATUS_EXPIRED);
                        entity.setVerificationStatus(PublicWarning.VERIFY_EXPIRED);
                        warningDao.insertOrUpdate(entity);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
}
