package com.blackout.emergency.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.blackout.emergency.warnings.location.WarningGeographicFilter;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying digitally signed official public emergency warnings (Phase 7).
 */
public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private final List<PublicWarning> alertList = new ArrayList<>();
    private OnAlertClickListener clickListener;
    private Double currentLatitude;
    private Double currentLongitude;

    public interface OnAlertClickListener {
        void onAlertClick(PublicWarning alert);
    }

    public void setOnAlertClickListener(OnAlertClickListener listener) {
        this.clickListener = listener;
    }

    public void setDeviceLocation(Double latitude, Double longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        notifyDataSetChanged();
    }

    public void setAlerts(List<PublicWarning> newAlerts) {
        this.alertList.clear();
        if (newAlerts != null) {
            this.alertList.addAll(newAlerts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        PublicWarning alert = alertList.get(position);
        holder.bind(alert, currentLatitude, currentLongitude, clickListener);
    }

    @Override
    public int getItemCount() {
        return alertList.size();
    }

    public static class AlertViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvAlertSeverity;
        private final TextView tvAlertRevision;
        private final TextView tvAlertVerification;
        private final TextView tvAlertAuthority;
        private final TextView tvAlertRelevance;
        private final TextView tvAlertTitle;
        private final TextView tvAlertArea;
        private final TextView tvAlertInstructions;
        private final TextView tvAlertPublished;
        private final TextView tvAlertExpiry;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertSeverity = itemView.findViewById(R.id.tv_alert_severity);
            tvAlertRevision = itemView.findViewById(R.id.tv_alert_revision);
            tvAlertVerification = itemView.findViewById(R.id.tv_alert_verification);
            tvAlertAuthority = itemView.findViewById(R.id.tv_alert_authority);
            tvAlertRelevance = itemView.findViewById(R.id.tv_alert_relevance);
            tvAlertTitle = itemView.findViewById(R.id.tv_alert_title);
            tvAlertArea = itemView.findViewById(R.id.tv_alert_area);
            tvAlertInstructions = itemView.findViewById(R.id.tv_alert_instructions);
            tvAlertPublished = itemView.findViewById(R.id.tv_alert_published);
            tvAlertExpiry = itemView.findViewById(R.id.tv_alert_expiry);
        }

        public void bind(final PublicWarning alert,
                         final Double deviceLat,
                         final Double deviceLon,
                         final OnAlertClickListener listener) {
            Context context = itemView.getContext();

            // Severity Badge
            String severity = alert.getSeverity() != null ? alert.getSeverity() : PublicWarning.SEVERITY_WARNING;
            tvAlertSeverity.setText(severity.toUpperCase(Locale.ROOT));

            if (PublicWarning.SEVERITY_CRITICAL.equalsIgnoreCase(severity)
                    || Constants.SEVERITY_IMMEDIATE.equalsIgnoreCase(severity)) {
                tvAlertSeverity.setTextColor(ContextCompat.getColor(context, R.color.emergency_red));
                tvAlertSeverity.setBackgroundResource(R.drawable.bg_badge_emergency);
            } else if (PublicWarning.SEVERITY_WARNING.equalsIgnoreCase(severity)
                    || Constants.SEVERITY_SEVERE.equalsIgnoreCase(severity)) {
                tvAlertSeverity.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                tvAlertSeverity.setBackgroundResource(R.drawable.bg_badge_amber);
            } else {
                tvAlertSeverity.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvAlertSeverity.setBackgroundResource(R.drawable.bg_badge_teal);
            }

            // Revision Badge
            tvAlertRevision.setText("REV " + alert.getRevision());

            // Verification Badge
            String vStatus = alert.getVerificationStatus();
            if (PublicWarning.VERIFY_VERIFIED.equals(vStatus)) {
                tvAlertVerification.setText("VERIFIED (Ed25519)");
                tvAlertVerification.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvAlertVerification.setBackgroundResource(R.drawable.bg_badge_teal);
            } else if (PublicWarning.STATUS_REVOKED.equalsIgnoreCase(alert.getStatus())
                    || PublicWarning.VERIFY_REVOKED_WARNING.equals(vStatus)) {
                tvAlertVerification.setText("REVOKED");
                tvAlertVerification.setTextColor(ContextCompat.getColor(context, R.color.emergency_red));
                tvAlertVerification.setBackgroundResource(R.drawable.bg_badge_emergency);
            } else {
                tvAlertVerification.setText(vStatus != null ? vStatus : "UNVERIFIED");
                tvAlertVerification.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                tvAlertVerification.setBackgroundResource(R.drawable.bg_badge_amber);
            }

            // Geographic Relevance Badge
            WarningGeographicFilter filter = new WarningGeographicFilter(context);
            WarningGeographicFilter.RelevanceLevel relevance = filter.evaluateRelevance(alert, deviceLat, deviceLon);
            switch (relevance) {
                case IN_AFFECTED_ZONE:
                    tvAlertRelevance.setVisibility(View.VISIBLE);
                    tvAlertRelevance.setText("• IN TARGET ZONE");
                    tvAlertRelevance.setTextColor(ContextCompat.getColor(context, R.color.emergency_red));
                    break;
                case NEARBY_ZONE:
                    tvAlertRelevance.setVisibility(View.VISIBLE);
                    tvAlertRelevance.setText("• NEARBY ZONE");
                    tvAlertRelevance.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                    break;
                case GENERAL_BROADCAST:
                    tvAlertRelevance.setVisibility(View.VISIBLE);
                    tvAlertRelevance.setText("• ALL REGIONS");
                    tvAlertRelevance.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
                    break;
                case LOCATION_UNKNOWN:
                default:
                    tvAlertRelevance.setVisibility(View.GONE);
                    break;
            }

            // Text Fields
            tvAlertTitle.setText(alert.getTitle());
            tvAlertAuthority.setText(alert.getIssuingAuthority());

            String area = alert.getAffectedRegions() != null && !alert.getAffectedRegions().isEmpty()
                    ? String.join(", ", alert.getAffectedRegions())
                    : "General Civil Protection Region";
            tvAlertArea.setText(area);

            tvAlertInstructions.setText(alert.getDescription());

            // Timestamps
            tvAlertPublished.setText("Issued: " + DateTimeUtils.getRelativeTimeSpan(alert.getIssuedAt()));
            tvAlertExpiry.setText("Expires: in " + DateTimeUtils.formatRemainingTime(alert.getExpiresAt()));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAlertClick(alert);
                }
            });
        }
    }
}
