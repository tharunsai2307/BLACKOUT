package com.blackout.emergency.warnings.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.blackout.emergency.R;
import com.blackout.emergency.utils.DateTimeUtils;
import com.blackout.emergency.warnings.location.WarningGeographicFilter;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.google.android.material.button.MaterialButton;

/**
 * Inspection dialog displaying cryptographic authenticity verification proofs,
 * key identity, geographical relevance, and complete warning content (Phase 7).
 */
public class WarningDetailDialog extends Dialog {

    private final PublicWarning warning;
    private final Double deviceLat;
    private final Double deviceLon;

    public WarningDetailDialog(@NonNull Context context,
                               @NonNull PublicWarning warning,
                               @Nullable Double deviceLat,
                               @Nullable Double deviceLon) {
        super(context);
        this.warning = warning;
        this.deviceLat = deviceLat;
        this.deviceLon = deviceLon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_warning_detail);

        if (getWindow() != null) {
            getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvSeverity = findViewById(R.id.tv_detail_severity);
        TextView tvRevision = findViewById(R.id.tv_detail_revision);
        TextView tvVerification = findViewById(R.id.tv_detail_verification);
        TextView tvTitle = findViewById(R.id.tv_detail_title);
        TextView tvAuthority = findViewById(R.id.tv_detail_authority);
        TextView tvDescription = findViewById(R.id.tv_detail_description);

        TextView tvRelevanceStatus = findViewById(R.id.tv_detail_relevance_status);
        TextView tvAffectedRegions = findViewById(R.id.tv_detail_affected_regions);
        TextView tvCoordinates = findViewById(R.id.tv_detail_coordinates);

        TextView tvKeyId = findViewById(R.id.tv_detail_key_id);
        TextView tvAlgorithm = findViewById(R.id.tv_detail_algorithm);
        TextView tvPayloadHash = findViewById(R.id.tv_detail_payload_hash);
        TextView tvSignatureSnippet = findViewById(R.id.tv_detail_signature_snippet);

        TextView tvIssuedAt = findViewById(R.id.tv_detail_issued_at);
        TextView tvExpiresAt = findViewById(R.id.tv_detail_expires_at);
        TextView tvTransportSource = findViewById(R.id.tv_detail_transport_source);
        MaterialButton btnClose = findViewById(R.id.btn_detail_close);

        // Bind Data
        tvTitle.setText(warning.getTitle());
        tvAuthority.setText("Issuing Authority: " + warning.getIssuingAuthority());
        tvDescription.setText(warning.getDescription());

        tvSeverity.setText(warning.getSeverity() != null ? warning.getSeverity() : "CRITICAL");
        tvRevision.setText("REVISION " + warning.getRevision());

        String vStatus = warning.getVerificationStatus();
        if (PublicWarning.VERIFY_VERIFIED.equals(vStatus)) {
            tvVerification.setText("VERIFIED (Ed25519)");
            tvVerification.setTextColor(ContextCompat.getColor(getContext(), R.color.teal_accent));
        } else if (PublicWarning.STATUS_REVOKED.equalsIgnoreCase(warning.getStatus())
                || PublicWarning.VERIFY_REVOKED_WARNING.equals(vStatus)) {
            tvVerification.setText("REVOKED");
            tvVerification.setTextColor(ContextCompat.getColor(getContext(), R.color.emergency_red));
        } else {
            tvVerification.setText(vStatus != null ? vStatus : "UNVERIFIED");
            tvVerification.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
        }

        // Geographic relevance
        WarningGeographicFilter filter = new WarningGeographicFilter(getContext());
        WarningGeographicFilter.RelevanceLevel relevance = filter.evaluateRelevance(warning, deviceLat, deviceLon);
        switch (relevance) {
            case IN_AFFECTED_ZONE:
                tvRelevanceStatus.setText("• YOUR DEVICE IS IN THE DIRECT AFFECTED ZONE");
                tvRelevanceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.emergency_red));
                break;
            case NEARBY_ZONE:
                tvRelevanceStatus.setText("• ADJACENT / NEARBY HAZARD SECTOR");
                tvRelevanceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
                break;
            case GENERAL_BROADCAST:
                tvRelevanceStatus.setText("• APPLIES TO ALL CIVIL DEFENSE REGIONS");
                tvRelevanceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.teal_accent));
                break;
            case LOCATION_UNKNOWN:
            default:
                tvRelevanceStatus.setText("• DEVICE LOCATION CURRENTLY UNAVAILABLE");
                tvRelevanceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.text_muted));
                break;
        }

        String regions = warning.getAffectedRegions() != null && !warning.getAffectedRegions().isEmpty()
                ? String.join(", ", warning.getAffectedRegions())
                : "All Regions";
        tvAffectedRegions.setText("Target Sectors: " + regions);

        if (warning.getLatitude() != null && warning.getLongitude() != null) {
            double rad = warning.getRadiusMeters() != null ? (warning.getRadiusMeters() / 1000.0) : 10.0;
            tvCoordinates.setText(String.format("Epicenter: %.6f, %.6f (Radius: %.2f km)",
                    warning.getLatitude(), warning.getLongitude(), rad));
            tvCoordinates.setVisibility(View.VISIBLE);
        } else {
            tvCoordinates.setVisibility(View.GONE);
        }

        // Cryptographic Details
        tvKeyId.setText("Signing Key ID: " + warning.getSigningKeyId());
        tvAlgorithm.setText("Algorithm: RFC 8032 " + warning.getSignatureAlgorithm());

        String hash = warning.getPayloadHash();
        tvPayloadHash.setText("SHA-256 Canonical Hash: " + (hash != null ? hash : "Generated locally on demand"));

        String sig = warning.getSignature();
        tvSignatureSnippet.setText("Digital Signature (Base64):\n" + (sig != null ? sig : "No signature affixed"));

        tvIssuedAt.setText("Issued: " + DateTimeUtils.formatIsoTimestamp(warning.getIssuedAt()));
        tvExpiresAt.setText("Expires: " + DateTimeUtils.formatIsoTimestamp(warning.getExpiresAt()));

        String transport = warning.getSourceDeviceId() != null && !warning.getSourceDeviceId().isEmpty()
                ? "Transport: P2P Multi-Hop Mesh (Relayed via " + warning.getSourceDeviceId() + ")"
                : "Transport: Direct Central Cloud Gateway Download";
        tvTransportSource.setText(transport);

        btnClose.setOnClickListener(v -> dismiss());
    }
}
