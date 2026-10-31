package com.blackout.emergency.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility functions for formatting timestamps in emergency messages and alerts.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        // Prevent instantiation
    }

    /**
     * Converts an epoch timestamp (milliseconds) into a human-readable relative time string.
     */
    public static String getRelativeTimeSpan(long timestampMillis) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMillis;

        if (diff < 0) {
            return "Just now";
        }

        long seconds = diff / 1000;
        if (seconds < 60) {
            return "Just now";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min" + (minutes > 1 ? "s" : "") + " ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hr" + (hours > 1 ? "s" : "") + " ago";
        }

        long days = hours / 24;
        if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return dateFormat.format(new Date(timestampMillis));
    }

    /**
     * Formats an epoch timestamp into standard date-time string.
     */
    public static String formatFullDateTime(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestampMillis));
    }

    /**
     * Formats an epoch timestamp into standard time of day (HH:mm:ss).
     */
    public static String formatRemainingTime(long expiresAtMillis) {
        long diff = expiresAtMillis - System.currentTimeMillis();
        if (diff <= 0) {
            return "Expired";
        }
        long minutes = diff / (60 * 1000);
        if (minutes < 60) {
            return minutes + " min" + (minutes > 1 ? "s" : "");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hr" + (hours > 1 ? "s" : "");
        }
        long days = hours / 24;
        return days + " day" + (days > 1 ? "s" : "");
    }

    public static String formatIsoTimestamp(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timestampMillis));
    }

    public static String formatTime(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestampMillis));
    }
}
