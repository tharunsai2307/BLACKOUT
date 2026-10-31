package com.blackout.emergency.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.adapters.AlertAdapter;
import com.blackout.emergency.services.LocationService;
import com.blackout.emergency.warnings.database.WarningExpiryManager;
import com.blackout.emergency.warnings.database.WarningRepository;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.relay.WarningRelayManager;
import com.blackout.emergency.warnings.sync.WarningSyncWorker;
import com.blackout.emergency.warnings.ui.WarningDetailDialog;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

/**
 * Public Safety Alerts Fragment: Displays digitally signed civil emergency warnings (Phase 7).
 * Connects Room database querying, signature verification state, geographic relevance evaluation,
 * and live offline multi-hop relay event updates.
 */
public class AlertsFragment extends Fragment {

    private RecyclerView recyclerAlerts;
    private View layoutEmptyState;
    private AlertAdapter alertAdapter;
    private final List<PublicWarning> rawWarningsList = new ArrayList<>();

    private WarningRepository warningRepository;
    private WarningExpiryManager expiryManager;
    private WarningRelayManager relayManager;
    private Handler mainHandler;

    private int activeFilterId = R.id.chip_filter_all_alerts;
    private Double currentLatitude;
    private Double currentLongitude;

    private final WarningRelayManager.WarningRelayListener relayListener = new WarningRelayManager.WarningRelayListener() {
        @Override
        public void onWarningRelayStatus(@NonNull String statusMessage) {}

        @Override
        public void onWarningTransferred(@NonNull String warningId, int revision, @NonNull String peerDeviceId) {}

        @Override
        public void onWarningReceivedVerified(@NonNull PublicWarning warning, @NonNull String peerDeviceId) {
            postToMain(() -> loadWarningsFromDatabase());
        }
    };

    public static AlertsFragment newInstance() {
        return new AlertsFragment();
    }

    private void postToMain(Runnable r) {
        if (r == null) return;
        if (mainHandler != null) {
            mainHandler.post(r);
        } else {
            r.run();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        warningRepository = WarningRepository.getInstance(requireContext());
        expiryManager = WarningExpiryManager.getInstance(requireContext());
        relayManager = WarningRelayManager.getInstance(requireContext());

        recyclerAlerts = view.findViewById(R.id.recycler_alerts);
        layoutEmptyState = view.findViewById(R.id.layout_alerts_empty);
        ChipGroup chipGroupFilter = view.findViewById(R.id.chip_group_alerts_filter);

        // RecyclerView setup
        recyclerAlerts.setLayoutManager(new LinearLayoutManager(getContext()));
        alertAdapter = new AlertAdapter();
        recyclerAlerts.setAdapter(alertAdapter);

        alertAdapter.setOnAlertClickListener(alert -> {
            if (getContext() != null) {
                WarningDetailDialog dialog = new WarningDetailDialog(
                        requireContext(),
                        alert,
                        currentLatitude,
                        currentLongitude
                );
                dialog.show();
            }
        });

        // Filter chips logic
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                activeFilterId = checkedIds.get(0);
            } else {
                activeFilterId = R.id.chip_filter_all_alerts;
            }
            applyActiveFilter();
        });

        // Register relay updates
        relayManager.addListener(relayListener);

        // Check GPS coordinates for relevance calculation
        fetchLocationAsync();

        // Enqueue background network download if connectivity is present
        WarningSyncWorker.enqueueOneTimeSync(requireContext());

        // Initial sweep and database load
        expiryManager.sweepExpiredWarnings(this::loadWarningsFromDatabase);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWarningsFromDatabase();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (relayManager != null) {
            relayManager.removeListener(relayListener);
        }
    }

    private void fetchLocationAsync() {
        LocationService locationService = LocationService.getInstance(requireContext());
        if (locationService.hasLocationPermission()) {
            locationService.captureCurrentLocation(new LocationService.LocationCallback() {
                @Override
                public void onStatusUpdate(@NonNull String statusMessage) {}

                @Override
                public void onLocationResolved(@NonNull LocationService.LocationResult result) {
                    if (result.hasCoordinates() && result.getLatitude() != null && result.getLongitude() != null) {
                        currentLatitude = result.getLatitude();
                        currentLongitude = result.getLongitude();
                        postToMain(() -> alertAdapter.setDeviceLocation(currentLatitude, currentLongitude));
                    }
                }
            });
        }
    }

    private void loadWarningsFromDatabase() {
        warningRepository.getAllWarnings(warnings -> {
            postToMain(() -> {
                rawWarningsList.clear();
                if (warnings != null) {
                    rawWarningsList.addAll(warnings);
                }
                applyActiveFilter();
            });
        });
    }

    private void applyActiveFilter() {
        long now = System.currentTimeMillis();
        List<PublicWarning> filtered = new ArrayList<>();

        for (PublicWarning w : rawWarningsList) {
            // Expired warnings hidden from active feed unless revoked
            if (w.getExpiresAt() <= now && !PublicWarning.STATUS_REVOKED.equalsIgnoreCase(w.getStatus())) {
                continue;
            }

            if (activeFilterId == R.id.chip_filter_critical_alerts) {
                if (PublicWarning.SEVERITY_CRITICAL.equalsIgnoreCase(w.getSeverity())) {
                    filtered.add(w);
                }
            } else if (activeFilterId == R.id.chip_filter_advisories) {
                if (PublicWarning.SEVERITY_ADVISORY.equalsIgnoreCase(w.getSeverity())
                        || PublicWarning.SEVERITY_INFORMATIONAL.equalsIgnoreCase(w.getSeverity())
                        || PublicWarning.SEVERITY_WATCH.equalsIgnoreCase(w.getSeverity())) {
                    filtered.add(w);
                }
            } else if (activeFilterId == R.id.chip_filter_weather) {
                String cat = w.getCategory() != null ? w.getCategory().toUpperCase() : "";
                if (cat.contains("FLOOD") || cat.contains("CYCLONE") || cat.contains("RAINFALL")) {
                    filtered.add(w);
                }
            } else {
                // All alerts
                filtered.add(w);
            }
        }

        if (filtered.isEmpty()) {
            recyclerAlerts.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerAlerts.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            alertAdapter.setAlerts(filtered);
        }
    }
}
