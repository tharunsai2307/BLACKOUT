package com.blackout.emergency.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.blackout.emergency.R;
import com.blackout.emergency.activities.MainActivity;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.Locale;

/**
 * Home Fragment: Main mission-control dashboard for BLACKOUT.
 * Displays offline connection status, prominent emergency SOS trigger,
 * real-time recent local emergency activity from Room database,
 * and quick-navigation overview cards.
 */
public class HomeFragment extends Fragment {

    private View layoutActivityEmpty;
    private View layoutActivityContent;
    private TextView tvRecentPriority;
    private TextView tvRecentCategory;
    private TextView tvRecentTime;
    private TextView tvRecentDescription;
    private TextView tvRecentLocation;

    // Network status views
    private View viewHomeStatusDot;
    private TextView tvHomeStatusBadge;
    private TextView tvHomeOfflineNotice;
    private TextView tvHomePeersCount;
    private TextView tvHomePeersSummary;

    private final InternetGatewayService.ConnectivityListener connectivityListener = (isConnected, networkType) -> {
        if (!isAdded() || getContext() == null) return;
        updateNetworkStatusUi();
    };

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnTriggerSos = view.findViewById(R.id.btn_home_trigger_sos);
        View cardSos = view.findViewById(R.id.card_home_sos);
        TextView btnGotoNearby = view.findViewById(R.id.btn_home_goto_nearby);
        TextView btnGotoHistory = view.findViewById(R.id.btn_home_goto_history);
        TextView btnGotoAlerts = view.findViewById(R.id.btn_home_goto_alerts);

        layoutActivityEmpty = view.findViewById(R.id.layout_home_activity_empty);
        layoutActivityContent = view.findViewById(R.id.layout_home_activity_content);
        tvRecentPriority = view.findViewById(R.id.tv_home_recent_priority);
        tvRecentCategory = view.findViewById(R.id.tv_home_recent_category);
        tvRecentTime = view.findViewById(R.id.tv_home_recent_time);
        tvRecentDescription = view.findViewById(R.id.tv_home_recent_description);
        tvRecentLocation = view.findViewById(R.id.tv_home_recent_location);

        viewHomeStatusDot = view.findViewById(R.id.view_home_status_dot);
        tvHomeStatusBadge = view.findViewById(R.id.tv_home_status_badge);
        tvHomeOfflineNotice = view.findViewById(R.id.tv_home_offline_notice);
        tvHomePeersCount = view.findViewById(R.id.tv_home_peers_count);
        tvHomePeersSummary = view.findViewById(R.id.tv_home_peers_summary);

        // Prominent Emergency SOS Navigation
        View.OnClickListener sosClickListener = v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToSos();
            }
        };
        btnTriggerSos.setOnClickListener(sosClickListener);
        cardSos.setOnClickListener(sosClickListener);

        // Quick Navigation to Nearby Mesh Screen
        btnGotoNearby.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.navigation_nearby);
            }
        });

        // Quick Navigation to History Screen
        btnGotoHistory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.navigation_history);
            }
        });
        if (layoutActivityContent != null) {
            layoutActivityContent.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTab(R.id.navigation_history);
                }
            });
        }

        // Quick Navigation to Alerts Screen
        btnGotoAlerts.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.navigation_alerts);
            }
        });

        // Register live Internet Gateway connectivity monitoring
        InternetGatewayService.getInstance(requireContext()).registerListener(connectivityListener);

        loadRecentActivity();
        updateNetworkStatusUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            InternetGatewayService.getInstance(requireContext()).unregisterListener(connectivityListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentActivity();
        updateNetworkStatusUi();
    }

    private void updateNetworkStatusUi() {
        if (getContext() == null || viewHomeStatusDot == null) return;

        com.blackout.emergency.nearby.NearbyConnectionManager manager =
                com.blackout.emergency.nearby.NearbyConnectionManager.getInstance(requireContext());
        com.blackout.emergency.nearby.NearbyDevice connectedPeer = manager.getConnectedDevice();

        InternetGatewayService gateway = InternetGatewayService.getInstance(requireContext());
        boolean isGatewayOnline = gateway.isInternetAvailable();

        if (connectedPeer != null) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText(R.string.nearby_badge_connected);
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
            tvHomeOfflineNotice.setText(String.format("Connected to %s via Nearby P2P Cluster.", connectedPeer.getEndpointName()));

            tvHomePeersCount.setText(String.format("1 Active Peer Link (%s)", connectedPeer.getEndpointName()));
            tvHomePeersSummary.setText("Direct offline wireless link established. Emergency SOS payloads can be exchanged.");
        } else if (manager.isAdvertising()) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText(R.string.nearby_badge_advertising);
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            tvHomeOfflineNotice.setText(String.format("Advertising presence as %s...", manager.getLocalCallsign()));

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText("Broadcasting radio presence. Ready for incoming peer connections.");
        } else if (manager.isDiscovering()) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText(R.string.nearby_badge_discovering);
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            tvHomeOfflineNotice.setText("Scanning for nearby BLACKOUT nodes...");

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText("Discovery active. Searching for advertising peers within radio range.");
        } else if (isGatewayOnline) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText("GATEWAY ONLINE");
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            tvHomeOfflineNotice.setText(String.format("Internet Gateway active (%s). Auto-syncing emergency alerts to central cloud backend.", gateway.getCurrentNetworkType()));

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText("Connected to central emergency infrastructure. Radio transceivers on standby.");
        } else {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            tvHomeStatusBadge.setText(R.string.badge_status_idle);
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
            tvHomeOfflineNotice.setText(R.string.home_network_status_offline);

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText(R.string.home_peers_summary);
        }
    }

    private void loadRecentActivity() {
        if (getContext() == null || layoutActivityEmpty == null || layoutActivityContent == null) {
            return;
        }

        EmergencyRepository.getInstance(requireContext()).getAllMessages(this::displayRecentActivity);
    }

    private void displayRecentActivity(List<EmergencyMessage> messages) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (messages == null || messages.isEmpty()) {
            layoutActivityEmpty.setVisibility(View.VISIBLE);
            layoutActivityContent.setVisibility(View.GONE);
        } else {
            layoutActivityEmpty.setVisibility(View.GONE);
            layoutActivityContent.setVisibility(View.VISIBLE);

            // Latest message is at index 0 because DAO orders by createdAt DESC
            EmergencyMessage latest = messages.get(0);

            // Priority
            String priority = latest.getPriority() != null ? latest.getPriority() : Constants.PRIORITY_MODERATE;
            tvRecentPriority.setText(priority.toUpperCase(Locale.ROOT));
            if (Constants.PRIORITY_CRITICAL.equalsIgnoreCase(priority)) {
                tvRecentPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.emergency_red));
                tvRecentPriority.setBackgroundResource(R.drawable.bg_badge_emergency);
            } else if (Constants.PRIORITY_HIGH.equalsIgnoreCase(priority)) {
                tvRecentPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
                tvRecentPriority.setBackgroundResource(R.drawable.bg_badge_amber);
            } else {
                tvRecentPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
                tvRecentPriority.setBackgroundResource(R.drawable.bg_badge_teal);
            }

            // Category
            String category = latest.getCategory() != null ? latest.getCategory() : "EMERGENCY";
            tvRecentCategory.setText(category.toUpperCase(Locale.ROOT));

            // Time
            tvRecentTime.setText(DateTimeUtils.getRelativeTimeSpan(latest.getTimestamp()));

            // Description
            tvRecentDescription.setText(latest.getDescription());

            // Location
            if (latest.hasCoordinates()) {
                String acc = latest.getLocationAccuracy() != null ? String.format(Locale.getDefault(), " (±%.1fm)", latest.getLocationAccuracy()) : "";
                tvRecentLocation.setText(String.format(Locale.getDefault(), "Location: %.5f° N, %.5f° E%s",
                        latest.getLatitude(), latest.getLongitude(), acc));
                tvRecentLocation.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            } else {
                tvRecentLocation.setText("Location: Not Available (No GPS Fix)");
                tvRecentLocation.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
            }
        }
    }
}
