package com.blackout.emergency.warnings.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.blackout.emergency.R;
import com.blackout.emergency.activities.MainActivity;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.models.PublicWarning;

/**
 * System notification manager for digitally signed public emergency warnings (Phase 7).
 * Dispatches distinct high-priority alerts for new warnings, updates, and revocations
 * while preventing duplicate alerts for identical revisions.
 */
public class WarningNotificationManager {

    public static final String CHANNEL_ID_WARNINGS = "channel_blackout_warnings";
    private static final String PREF_NOTIFIED_WARNINGS = "notified_warnings_set";

    private static volatile WarningNotificationManager instance;
    private final Context context;
    private final NotificationManager notificationManager;
    private final SharedPreferences prefs;

    public WarningNotificationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    public static synchronized WarningNotificationManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new WarningNotificationManager(context);
        }
        return instance;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_WARNINGS,
                    "Public Emergency Warnings",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Official civil defense warnings cryptographically signed by authorized authorities");
            channel.enableVibration(true);
            channel.enableLights(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Dispatches notification for an incoming or updated warning.
     */
    public void notifyWarning(@NonNull PublicWarning warning) {
        // Deduplication check: do not alert twice for identical warningId and revision
        String notificationKey = warning.getWarningId() + "_r" + warning.getRevision();
        if (prefs.getBoolean("notified_" + notificationKey, false)) {
            return;
        }

        boolean isCritical = PublicWarning.SEVERITY_CRITICAL.equalsIgnoreCase(warning.getSeverity());
        boolean isRevocation = PublicWarning.STATUS_REVOKED.equalsIgnoreCase(warning.getStatus())
                || PublicWarning.VERIFY_REVOKED_WARNING.equals(warning.getVerificationStatus());

        String title;
        String contentText;

        if (isRevocation) {
            title = "⚠️ WARNING CANCELLED: " + warning.getTitle();
            contentText = "Notice from " + warning.getIssuingAuthority() + ": Warning has been formally revoked.";
        } else if (warning.getRevision() > 1) {
            title = "🚨 UPDATED ALERT [Rev " + warning.getRevision() + "]: " + warning.getTitle();
            contentText = warning.getDescription();
        } else {
            title = (isCritical ? "🚨 CRITICAL WARNING: " : "⚠️ OFFICIAL ALERT: ") + warning.getTitle();
            contentText = warning.getDescription();
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("navigate_to_tab", "ALERTS");
        intent.putExtra("selected_warning_id", warning.getWarningId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_WARNINGS)
                .setSmallIcon(R.drawable.ic_priority_high)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText + "\n\nTarget: " + String.join(", ", warning.getAffectedRegions())))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(ContextCompat.getColor(context, isCritical ? R.color.emergency_red : R.color.teal_accent))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        int notifId = Math.abs(warning.getWarningId().hashCode() + warning.getRevision());
        if (notificationManager != null) {
            notificationManager.notify(notifId, builder.build());
        }

        // Mark as notified
        prefs.edit().putBoolean("notified_" + notificationKey, true).apply();
    }
}
