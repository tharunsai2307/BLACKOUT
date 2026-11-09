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
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.nearby.NearbyDevice;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.Locale;

/**
 * Home Fragment: Main mission-control dashboard for BLACKOUT.
 * Displays offline connection status, real-time autonomous mesh peer counts,
 * internet gateway availability, prominent emergency SOS trigger,
 * and recent local emergency activity from Room database.
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
    private TextView tvHomeGreeting;

    private final InternetGatewayService.ConnectivityListener connectivityListener = (isConnected, networkType) -> {
        if (!isAdded() || getContext() == null) return;
        updateNetworkStatusUi();
    };

    private final NearbyConnectionManager.NearbyEventListener nearbyEventListener =
            new NearbyConnectionManager.SimpleNearbyEventListener() {
                @Override
                public void onConnectionResult(@NonNull String endpointId, boolean isConnected, @NonNull String message) {
                    if (isAdded()) updateNetworkStatusUi();
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    if (isAdded()) updateNetworkStatusUi();
                }

                @Override
                public void onDiscoveryStateChanged(boolean isDiscovering) {
                    if (isAdded()) updateNetworkStatusUi();
                }

                @Override
                public void onAdvertisingStateChanged(boolean isAdvertising) {
                    if (isAdded()) updateNetworkStatusUi();
                }
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
        tvHomeGreeting = view.findViewById(R.id.tv_home_greeting);

        // Prominent Emergency SOS Navigation
        View.OnClickListener sosClickListener = v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToSos();
            }
        };
        btnTriggerSos.setOnClickListener(sosClickListener);
        cardSos.setOnClickListener(sosClickListener);

        // 3D Tactical breathing pulse animation on primary emergency trigger
        try {
            android.view.animation.Animation pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_3d_beacon);
            btnTriggerSos.startAnimation(pulseAnim);
        } catch (Exception ignored) {}

        // Quick Navigation to Nearby Mesh Screen
        btnGotoNearby.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openMeshRadar();
            }
        });

        // Quick Navigation to History Screen
        btnGotoHistory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.navigation_history);
            }
        });

        // Quick Navigation to Alerts Screen
        btnGotoAlerts.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.navigation_alerts);
            }
        });

        // Register live Internet Gateway and Mesh connectivity monitoring
        InternetGatewayService.getInstance(requireContext()).registerListener(connectivityListener);
        NearbyConnectionManager.getInstance(requireContext()).registerListener(nearbyEventListener);

        loadRecentActivity();
        updateNetworkStatusUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            InternetGatewayService.getInstance(requireContext()).unregisterListener(connectivityListener);
            NearbyConnectionManager.getInstance(requireContext()).unregisterListener(nearbyEventListener);
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

        NearbyConnectionManager manager = NearbyConnectionManager.getInstance(requireContext());
        List<NearbyDevice> connectedPeers = manager.getConnectedDevices();
        int peerCount = connectedPeers.size();
        boolean hasGatewayPeer = false;
        String gatewayNetType = null;
        for (NearbyDevice dev : connectedPeers) {
            if (dev.isGateway()) {
                hasGatewayPeer = true;
                gatewayNetType = dev.getNetworkType();
                break;
            }
        }

        InternetGatewayService gateway = InternetGatewayService.getInstance(requireContext());
        boolean isLocalGateway = gateway.isInternetAvailable();

        SessionManager sessionManager = SessionManager.getInstance(requireContext());
        if (tvHomeGreeting != null) {
            if (sessionManager.isLoggedIn()) {
                String name = sessionManager.getFullName();
                tvHomeGreeting.setText("Welcome, " + (name != null && !name.isEmpty() ? name : "Responder"));
            } else {
                tvHomeGreeting.setText("Offline Guest Mode • Mesh Ready");
            }
        }

        if (peerCount > 0) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            if (hasGatewayPeer) {
                tvHomeStatusBadge.setText("GATEWAY ROUTE ACTIVE");
                tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
                tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
                tvHomeOfflineNotice.setText(String.format("Mesh route to Gateway (%s) active via %d peer(s).",
                        gatewayNetType != null ? gatewayNetType : "Cloud", peerCount));
            } else if (isLocalGateway) {
                tvHomeStatusBadge.setText("LOCAL GATEWAY ACTIVE");
                tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
                tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
                tvHomeOfflineNotice.setText(String.format("Internet Gateway active (%s) with %d mesh peer(s) bridged.",
                        gateway.getCurrentNetworkType(), peerCount));
            } else {
                tvHomeStatusBadge.setText(R.string.nearby_badge_connected);
                tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success));
                tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
                tvHomeOfflineNotice.setText(String.format("Connected to %d peer(s) via autonomous mesh.", peerCount));
            }

            tvHomePeersCount.setText(String.format(Locale.ROOT, "%d Active Mesh Peer%s", peerCount, peerCount == 1 ? "" : "s"));
            tvHomePeersSummary.setText("Autonomous store-and-forward mesh active. Emergency alerts synchronizing across hops.");
        } else if (isLocalGateway) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText("GATEWAY ONLINE");
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            tvHomeOfflineNotice.setText(String.format("Internet Gateway active (%s). Auto-syncing emergency alerts to cloud backend.", gateway.getCurrentNetworkType()));

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText("Connected to central emergency infrastructure. Mesh radios actively scanning.");
        } else if (manager.isDiscovering() || manager.isAdvertising()) {
            viewHomeStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvHomeStatusBadge.setText("MESH SCANNING");
            tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvHomeStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            tvHomeOfflineNotice.setText(String.format("Autonomous mesh active as %s. Scanning for emergency peers...", manager.getLocalCallsign()));

            tvHomePeersCount.setText(R.string.home_peers_count_zero);
            tvHomePeersSummary.setText("Radio transceivers discovering peers in range. Automatic store-and-forward enabled.");
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

            // Category & Time
            tvRecentCategory.setText(latest.getCategory() != null ? latest.getCategory() : Constants.CATEGORY_OTHER);
            long timestamp = latest.getCreatedAt() != null ? latest.getCreatedAt() : System.currentTimeMillis();
            tvRecentTime.setText(DateTimeUtils.getRelativeTimeSpan(timestamp));

            // Description
            String desc = latest.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                tvRecentDescription.setText(desc);
                tvRecentDescription.setVisibility(View.VISIBLE);
            } else {
                tvRecentDescription.setVisibility(View.GONE);
            }

            // Location
            String loc = latest.getLocationDisplayString();
            if (loc != null && !loc.trim().isEmpty()) {
                tvRecentLocation.setText(loc);
                tvRecentLocation.setVisibility(View.VISIBLE);
            } else {
                tvRecentLocation.setVisibility(View.GONE);
            }
        }
    }
}
