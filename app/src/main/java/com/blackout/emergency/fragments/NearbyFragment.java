package com.blackout.emergency.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.adapters.NearbyDeviceAdapter;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.blackout.emergency.gateway.PendingUploadManager;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.nearby.NearbyDevice;
import com.blackout.emergency.nearby.ReceivedMessageHandler;
import com.blackout.emergency.relay.RelayEngine;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Nearby Network Fragment implementing real device-to-device wireless
 * communications via Google Nearby Connections (P2P_CLUSTER).
 */
public class NearbyFragment extends Fragment implements NearbyConnectionManager.NearbyEventListener {

    private TextView tvMyIdentity;
    private View viewStatusDot;
    private TextView tvRadioState;
    private TextView tvStatusPill;
    private TextView tvDiscoveryDesc;
    private MaterialButton btnAdvertise;
    private MaterialButton btnDiscover;
    private MaterialButton btnStopRadio;

    // Connection Approval Card
    private MaterialCardView cardApproval;
    private TextView tvApprovalDevice;
    private TextView tvAuthCode;
    private MaterialButton btnAccept;
    private MaterialButton btnReject;

    // Active Connected Peer Card
    private MaterialCardView cardConnected;
    private TextView tvConnectedName;
    private TextView tvConnectedEndpoint;
    private MaterialButton btnDisconnect;

    // SOS Message Transfer Card
    private MaterialCardView cardTransfer;
    private MaterialButton btnSelectSos;
    private View layoutSelectedSos;
    private TextView tvSelectedPriority;
    private TextView tvSelectedCategory;
    private TextView tvSelectedDesc;
    private TextView tvSelectedLocation;
    private MaterialButton btnSendSos;
    private View layoutTransferProgress;
    private ProgressBar pbTransfer;
    private TextView tvTransferStatus;

    // Discovered Devices Section
    private TextView tvDetectedTitle;
    private RecyclerView recyclerDevices;
    private MaterialCardView cardEmptyDevices;
    private NearbyDeviceAdapter deviceAdapter;

    // Relay Status Views
    private TextView tvRelayQueueBadge;
    private TextView tvRelayStatusDesc;

    // Gateway Status Views (Phase 5)
    private TextView tvGatewayStatusBadge;
    private TextView tvGatewayStatusDesc;
    private MaterialButton btnGatewaySyncNow;

    private NearbyConnectionManager connectionManager;
    private RelayEngine relayEngine;
    private String pendingApprovalEndpointId = null;
    private EmergencyMessage selectedOutgoingSos = null;

    private final InternetGatewayService.ConnectivityListener gatewayConnectivityListener = (isConnected, networkType) -> {
        if (!isAdded() || getContext() == null) return;
        updateGatewayStatusUi();
    };

    private final PendingUploadManager.GatewaySyncListener gatewaySyncListener = new PendingUploadManager.GatewaySyncListener() {
        @Override
        public void onSyncStarted() {
            if (!isAdded()) return;
            if (tvGatewayStatusDesc != null) {
                tvGatewayStatusDesc.setText("Synchronizing pending emergency alerts with cloud backend...");
            }
            if (btnGatewaySyncNow != null) {
                btnGatewaySyncNow.setEnabled(false);
            }
        }

        @Override
        public void onSyncProgress(int current, int total) {
            if (!isAdded()) return;
            if (tvGatewayStatusDesc != null) {
                tvGatewayStatusDesc.setText(String.format(Locale.ROOT, "Uploading alert %d of %d to central infrastructure...", current, total));
            }
        }

        @Override
        public void onSyncCompleted(int uploadedCount, int failedCount, int pendingRemaining) {
            if (!isAdded()) return;
            if (btnGatewaySyncNow != null) {
                btnGatewaySyncNow.setEnabled(true);
            }
            updateGatewayStatusUi();
            if (getContext() != null && uploadedCount > 0) {
                Toast.makeText(getContext(), "Internet Gateway: Uploaded " + uploadedCount + " alert(s) to cloud database.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final RelayEngine.RelayEventListener relayEventListener = new RelayEngine.RelayEventListener() {
        @Override
        public void onRelayStatusChanged(@NonNull String statusMessage) {
            if (!isAdded()) return;
            if (tvRelayStatusDesc != null) {
                tvRelayStatusDesc.setText(statusMessage);
            }
            updateRelayQueueBadge();
        }

        @Override
        public void onRelayProgress(@NonNull String endpointId, int completed, int total) {
            // Optional progress tracker
        }

        @Override
        public void onMessageRelayedSuccess(@NonNull String messageId, @NonNull String peerDeviceId, int hopNumber) {
            if (!isAdded() || getContext() == null) return;
            Toast.makeText(getContext(), "SOS [" + messageId.substring(0, Math.min(8, messageId.length())) +
                    "] successfully delivered to peer (Hop: " + hopNumber + ").", Toast.LENGTH_SHORT).show();
            updateRelayQueueBadge();
        }

        @Override
        public void onMessageRelayFailed(@NonNull String messageId, @Nullable String reason) {
            if (!isAdded() || getContext() == null) return;
            Toast.makeText(getContext(), "Relay failed for [" + messageId.substring(0, Math.min(8, messageId.length())) +
                    "]: " + reason, Toast.LENGTH_SHORT).show();
            updateRelayQueueBadge();
        }
    };

    private final ActivityResultLauncher<String[]> nearbyPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    Toast.makeText(getContext(), "Nearby wireless permissions granted.", Toast.LENGTH_SHORT).show();
                } else {
                    showPermissionsRequiredDialog();
                }
            });

    public static NearbyFragment newInstance() {
        return new NearbyFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nearby, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        connectionManager = NearbyConnectionManager.getInstance(requireContext());
        connectionManager.setListener(this);
        relayEngine = RelayEngine.getInstance(requireContext());
        relayEngine.addListener(relayEventListener);

        // Display local node identity
        tvMyIdentity.setText(String.format(getString(R.string.nearby_my_identity),
                connectionManager.getLocalCallsign(),
                connectionManager.getLocalDeviceId()));

        // Setup RecyclerView for Discovered Devices
        recyclerDevices.setLayoutManager(new LinearLayoutManager(getContext()));
        deviceAdapter = new NearbyDeviceAdapter();
        deviceAdapter.setOnDeviceClickListener(this::handleDeviceConnectClick);
        recyclerDevices.setAdapter(deviceAdapter);

        // Control button listeners
        btnAdvertise.setOnClickListener(v -> handleStartAdvertising());
        btnDiscover.setOnClickListener(v -> handleStartDiscovery());
        btnStopRadio.setOnClickListener(v -> handleStopRadio());

        // Approval button listeners
        btnAccept.setOnClickListener(v -> handleAcceptConnection());
        btnReject.setOnClickListener(v -> handleRejectConnection());

        // Disconnect button listener
        btnDisconnect.setOnClickListener(v -> handleDisconnectPeer());

        // SOS transfer button listeners
        btnSelectSos.setOnClickListener(v -> showSelectSosDialog());
        btnSendSos.setOnClickListener(v -> handleSendSos());

        // Gateway manual sync button listener
        btnGatewaySyncNow.setOnClickListener(v -> handleManualGatewaySync());

        // Register Internet Gateway and Cloud Sync listeners
        InternetGatewayService.getInstance(requireContext()).registerListener(gatewayConnectivityListener);
        PendingUploadManager.getInstance(requireContext()).registerSyncListener(gatewaySyncListener);

        // Sync initial UI with connection manager state
        updateUiState();
        updateGatewayStatusUi();
    }

    private void initViews(View view) {
        tvMyIdentity = view.findViewById(R.id.tv_nearby_my_identity);
        viewStatusDot = view.findViewById(R.id.view_nearby_status_dot);
        tvRadioState = view.findViewById(R.id.tv_nearby_radio_state);
        tvStatusPill = view.findViewById(R.id.tv_nearby_status_pill);
        tvDiscoveryDesc = view.findViewById(R.id.tv_nearby_discovery_desc);
        btnAdvertise = view.findViewById(R.id.btn_nearby_advertise);
        btnDiscover = view.findViewById(R.id.btn_nearby_discover);
        btnStopRadio = view.findViewById(R.id.btn_nearby_stop);

        cardApproval = view.findViewById(R.id.card_nearby_approval);
        tvApprovalDevice = view.findViewById(R.id.tv_nearby_approval_device);
        tvAuthCode = view.findViewById(R.id.tv_nearby_auth_code);
        btnAccept = view.findViewById(R.id.btn_nearby_accept);
        btnReject = view.findViewById(R.id.btn_nearby_reject);

        cardConnected = view.findViewById(R.id.card_nearby_connected);
        tvConnectedName = view.findViewById(R.id.tv_nearby_connected_name);
        tvConnectedEndpoint = view.findViewById(R.id.tv_nearby_connected_endpoint);
        btnDisconnect = view.findViewById(R.id.btn_nearby_disconnect);

        cardTransfer = view.findViewById(R.id.card_nearby_transfer);
        btnSelectSos = view.findViewById(R.id.btn_nearby_select_sos);
        layoutSelectedSos = view.findViewById(R.id.layout_nearby_selected_sos);
        tvSelectedPriority = view.findViewById(R.id.tv_nearby_selected_priority);
        tvSelectedCategory = view.findViewById(R.id.tv_nearby_selected_category);
        tvSelectedDesc = view.findViewById(R.id.tv_nearby_selected_desc);
        tvSelectedLocation = view.findViewById(R.id.tv_nearby_selected_location);
        btnSendSos = view.findViewById(R.id.btn_nearby_send_sos);
        layoutTransferProgress = view.findViewById(R.id.layout_nearby_transfer_progress);
        pbTransfer = view.findViewById(R.id.pb_nearby_transfer);
        tvTransferStatus = view.findViewById(R.id.tv_nearby_transfer_status);

        tvDetectedTitle = view.findViewById(R.id.tv_nearby_detected_title);
        recyclerDevices = view.findViewById(R.id.recycler_nearby_devices);
        cardEmptyDevices = view.findViewById(R.id.card_nearby_empty_devices);

        tvRelayQueueBadge = view.findViewById(R.id.tv_relay_queue_badge);
        tvRelayStatusDesc = view.findViewById(R.id.tv_relay_status_desc);

        tvGatewayStatusBadge = view.findViewById(R.id.tv_gateway_status_badge);
        tvGatewayStatusDesc = view.findViewById(R.id.tv_gateway_status_desc);
        btnGatewaySyncNow = view.findViewById(R.id.btn_gateway_sync_now);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRelayQueueBadge();
        updateGatewayStatusUi();
    }

    private void updateGatewayStatusUi() {
        if (!isAdded() || getContext() == null || tvGatewayStatusBadge == null) return;

        InternetGatewayService gateway = InternetGatewayService.getInstance(requireContext());
        boolean isOnline = gateway.isInternetAvailable();
        String networkType = gateway.getCurrentNetworkType();

        if (isOnline) {
            tvGatewayStatusBadge.setText("ONLINE (" + networkType.toUpperCase(Locale.ROOT) + ")");
            tvGatewayStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success));
            tvGatewayStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
        } else {
            tvGatewayStatusBadge.setText("OFFLINE");
            tvGatewayStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline));
            tvGatewayStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
        }

        PendingUploadManager.getInstance(requireContext()).getPendingCount(count -> {
            if (!isAdded()) return;
            if (isOnline) {
                if (count > 0) {
                    tvGatewayStatusDesc.setText(String.format(Locale.ROOT, "Active gateway on %s. %d emergency alert(s) queued for cloud sync.", networkType, count));
                } else {
                    tvGatewayStatusDesc.setText(String.format(Locale.ROOT, "Active gateway on %s. All emergency alerts synchronized with central database.", networkType));
                }
            } else {
                if (count > 0) {
                    tvGatewayStatusDesc.setText(String.format(Locale.ROOT, "%d emergency alert(s) buffered offline. Will automatically upload when internet connects.", count));
                } else {
                    tvGatewayStatusDesc.setText("Internet offline. Emergency SOS messages remain securely buffered in local Room database.");
                }
            }
        });
    }

    private void handleManualGatewaySync() {
        if (!isAdded() || getContext() == null) return;
        InternetGatewayService gateway = InternetGatewayService.getInstance(requireContext());
        if (!gateway.isInternetAvailable()) {
            Toast.makeText(getContext(), "Internet connection unavailable. Alerts remain safely queued.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "Initiating cloud sync...", Toast.LENGTH_SHORT).show();
        PendingUploadManager.getInstance(requireContext()).flushPendingUploads(count -> {
            if (isAdded() && getContext() != null) {
                updateGatewayStatusUi();
            }
        });
    }

    private void updateRelayQueueBadge() {
        if (!isAdded() || relayEngine == null || tvRelayQueueBadge == null) return;
        relayEngine.getQueueManager().getPendingRelayCount(count -> {
            if (!isAdded()) return;
            tvRelayQueueBadge.setText(String.format(getString(R.string.nearby_relay_queued_badge), count));
        });
    }

    private void handleStartAdvertising() {
        if (!checkAndRequestPermissions()) return;
        if (!checkHardwareEnabled()) return;

        connectionManager.startAdvertising();
    }

    private void handleStartDiscovery() {
        if (!checkAndRequestPermissions()) return;
        if (!checkHardwareEnabled()) return;

        connectionManager.startDiscovery();
    }

    private void handleStopRadio() {
        connectionManager.stopAll();
        pendingApprovalEndpointId = null;
        updateUiState();
    }

    private void handleDeviceConnectClick(@NonNull NearbyDevice device) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), "Requesting connection to " + device.getEndpointName() + "...", Toast.LENGTH_SHORT).show();
        connectionManager.requestConnection(device.getEndpointId());
    }

    private void handleAcceptConnection() {
        if (pendingApprovalEndpointId != null) {
            connectionManager.acceptConnection(pendingApprovalEndpointId);
            cardApproval.setVisibility(View.GONE);
        }
    }

    private void handleRejectConnection() {
        if (pendingApprovalEndpointId != null) {
            connectionManager.rejectConnection(pendingApprovalEndpointId);
            pendingApprovalEndpointId = null;
            cardApproval.setVisibility(View.GONE);
        }
    }

    private void handleDisconnectPeer() {
        NearbyDevice connected = connectionManager.getConnectedDevice();
        if (connected != null) {
            connectionManager.disconnectFromEndpoint(connected.getEndpointId());
        }
    }

    private void showSelectSosDialog() {
        if (getContext() == null) return;

        EmergencyRepository.getInstance(requireContext()).getOutgoingMessages(messages -> {
            if (!isAdded() || getContext() == null) return;

            if (messages == null || messages.isEmpty()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No Local SOS Alerts")
                        .setMessage(R.string.nearby_transfer_no_messages)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
                return;
            }

            CharSequence[] items = new CharSequence[messages.size()];
            for (int i = 0; i < messages.size(); i++) {
                EmergencyMessage m = messages.get(i);
                items[i] = String.format("[%s] %s - %s",
                        m.getPriority(),
                        m.getEmergencyCategory(),
                        DateTimeUtils.getRelativeTimeSpan(m.getTimestamp()));
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select Local SOS Alert")
                    .setItems(items, (dialog, which) -> {
                        selectedOutgoingSos = messages.get(which);
                        displaySelectedSos(selectedOutgoingSos);
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });
    }

    private void displaySelectedSos(@NonNull EmergencyMessage message) {
        layoutSelectedSos.setVisibility(View.VISIBLE);
        btnSendSos.setEnabled(true);

        tvSelectedPriority.setText(message.getPriority().toUpperCase(Locale.ROOT));
        if (Constants.PRIORITY_CRITICAL.equalsIgnoreCase(message.getPriority())) {
            tvSelectedPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.emergency_red));
            tvSelectedPriority.setBackgroundResource(R.drawable.bg_badge_emergency);
        } else if (Constants.PRIORITY_HIGH.equalsIgnoreCase(message.getPriority())) {
            tvSelectedPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
            tvSelectedPriority.setBackgroundResource(R.drawable.bg_badge_amber);
        } else {
            tvSelectedPriority.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvSelectedPriority.setBackgroundResource(R.drawable.bg_badge_teal);
        }

        tvSelectedCategory.setText(message.getEmergencyCategory().toUpperCase(Locale.ROOT));
        tvSelectedDesc.setText(message.getDescription());

        if (message.hasCoordinates()) {
            tvSelectedLocation.setText(String.format(Locale.getDefault(), "Location: %.5f° N, %.5f° E",
                    message.getLatitude(), message.getLongitude()));
        } else {
            tvSelectedLocation.setText("Location: Coordinates Not Available");
        }
    }

    private void handleSendSos() {
        if (selectedOutgoingSos == null) {
            Toast.makeText(getContext(), R.string.nearby_transfer_no_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        NearbyDevice connectedPeer = connectionManager.getConnectedDevice();
        if (connectedPeer == null) {
            Toast.makeText(getContext(), "No peer currently connected.", Toast.LENGTH_SHORT).show();
            return;
        }

        layoutTransferProgress.setVisibility(View.VISIBLE);
        tvTransferStatus.setText(String.format("Transmitting SOS payload to %s...", connectedPeer.getEndpointName()));
        btnSendSos.setEnabled(false);

        connectionManager.sendEmergencySos(connectedPeer.getEndpointId(), selectedOutgoingSos, new NearbyConnectionManager.NearbyPayloadCallback() {
            @Override
            public void onSuccess(long payloadId) {
                if (!isAdded() || getContext() == null) return;
                layoutTransferProgress.setVisibility(View.GONE);
                btnSendSos.setEnabled(true);

                // Update message status in Room to TRANSFERRED_TO_NEARBY_DEVICE
                EmergencyRepository.getInstance(requireContext())
                        .updateMessageStatus(selectedOutgoingSos.getMessageId(), Constants.STATUS_TRANSFERRED, null);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Payload Transmitted")
                        .setMessage(String.format(getString(R.string.nearby_transfer_success), connectedPeer.getEndpointName()))
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded() || getContext() == null) return;
                layoutTransferProgress.setVisibility(View.GONE);
                btnSendSos.setEnabled(true);

                // Update status to TRANSFER_FAILED
                EmergencyRepository.getInstance(requireContext())
                        .updateMessageStatus(selectedOutgoingSos.getMessageId(), Constants.STATUS_TRANSFER_FAILED, null);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Transmission Error")
                        .setMessage(String.format(getString(R.string.nearby_transfer_failed), errorMessage))
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }
        });
    }

    private boolean checkAndRequestPermissions() {
        if (getContext() == null) return false;
        if (!NearbyConnectionManager.hasRequiredPermissions(requireContext())) {
            nearbyPermissionsLauncher.launch(NearbyConnectionManager.getRequiredPermissions());
            return false;
        }
        return true;
    }

    private boolean checkHardwareEnabled() {
        if (getContext() == null) return false;
        if (!NearbyConnectionManager.isBluetoothEnabled()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Bluetooth Disabled")
                    .setMessage("Bluetooth is required for direct device-to-device wireless discovery and communication. Turn on Bluetooth in device settings.")
                    .setPositiveButton("Open Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return false;
        }

        if (!NearbyConnectionManager.isLocationEnabled(requireContext())) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Location Services Disabled")
                    .setMessage("Location services must be enabled on this device to discover nearby wireless radio endpoints.")
                    .setPositiveButton("Open Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return false;
        }

        return true;
    }

    private void showPermissionsRequiredDialog() {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Permissions Required")
                .setMessage("Bluetooth, Nearby Wi-Fi, and Location permissions are required to discover and connect with nearby devices without internet access.")
                .setPositiveButton("Grant Permissions", (d, w) -> nearbyPermissionsLauncher.launch(NearbyConnectionManager.getRequiredPermissions()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void updateUiState() {
        if (getContext() == null) return;

        NearbyDevice connectedPeer = connectionManager.getConnectedDevice();
        boolean isAdv = connectionManager.isAdvertising();
        boolean isDisc = connectionManager.isDiscovering();

        if (connectedPeer != null) {
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvRadioState.setText(String.format(getString(R.string.nearby_radio_state_connected), connectedPeer.getEndpointName()));
            tvStatusPill.setText(R.string.nearby_badge_connected);
            tvStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success));
            tvStatusPill.setBackgroundResource(R.drawable.bg_badge_green);
            tvDiscoveryDesc.setText(R.string.nearby_connected_card_desc);

            cardConnected.setVisibility(View.VISIBLE);
            tvConnectedName.setText(connectedPeer.getEndpointName());
            tvConnectedEndpoint.setText(String.format("Endpoint ID: %s • Direct P2P Radio Link", connectedPeer.getEndpointId()));

            cardTransfer.setVisibility(View.VISIBLE);

            btnAdvertise.setEnabled(false);
            btnDiscover.setEnabled(false);
            btnStopRadio.setEnabled(true);
        } else if (isAdv) {
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvRadioState.setText(String.format(getString(R.string.nearby_radio_state_advertising), connectionManager.getLocalCallsign()));
            tvStatusPill.setText(R.string.nearby_badge_advertising);
            tvStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvStatusPill.setBackgroundResource(R.drawable.bg_badge_teal);
            tvDiscoveryDesc.setText("Broadcasting radio presence. Keep discovering device in range.");

            cardConnected.setVisibility(View.GONE);
            cardTransfer.setVisibility(View.GONE);

            btnAdvertise.setEnabled(false);
            btnDiscover.setEnabled(false);
            btnStopRadio.setEnabled(true);
        } else if (isDisc) {
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active);
            tvRadioState.setText(R.string.nearby_radio_state_discovering);
            tvStatusPill.setText(R.string.nearby_badge_discovering);
            tvStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_accent));
            tvStatusPill.setBackgroundResource(R.drawable.bg_badge_teal);
            tvDiscoveryDesc.setText("Scanning radio bands for advertising nodes within range...");

            cardConnected.setVisibility(View.GONE);
            cardTransfer.setVisibility(View.GONE);

            btnAdvertise.setEnabled(false);
            btnDiscover.setEnabled(false);
            btnStopRadio.setEnabled(true);
        } else {
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            tvRadioState.setText(R.string.nearby_radio_state_idle);
            tvStatusPill.setText(R.string.nearby_badge_idle);
            tvStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline));
            tvStatusPill.setBackgroundResource(R.drawable.bg_badge_neutral);
            tvDiscoveryDesc.setText(R.string.nearby_radio_state_desc);

            cardConnected.setVisibility(View.GONE);
            cardTransfer.setVisibility(View.GONE);

            btnAdvertise.setEnabled(true);
            btnDiscover.setEnabled(true);
            btnStopRadio.setEnabled(false);
        }

        updateDiscoveredList();
    }

    private void updateDiscoveredList() {
        List<NearbyDevice> devices = connectionManager.getDiscoveredDevices();
        deviceAdapter.setDevices(devices);

        if (devices.isEmpty()) {
            recyclerDevices.setVisibility(View.GONE);
            cardEmptyDevices.setVisibility(View.VISIBLE);
            tvDetectedTitle.setText(getString(R.string.nearby_detected_title) + " (0)");
        } else {
            recyclerDevices.setVisibility(View.VISIBLE);
            cardEmptyDevices.setVisibility(View.GONE);
            tvDetectedTitle.setText(String.format(Locale.ROOT, "%s (%d)", getString(R.string.nearby_detected_title), devices.size()));
        }
    }

    // =========================================================================
    // NearbyEventListener Implementation
    // =========================================================================

    @Override
    public void onAdvertisingStateChanged(boolean isAdvertising) {
        if (!isAdded()) return;
        updateUiState();
    }

    @Override
    public void onDiscoveryStateChanged(boolean isDiscovering) {
        if (!isAdded()) return;
        updateUiState();
    }

    @Override
    public void onEndpointDiscovered(@NonNull NearbyDevice device) {
        if (!isAdded()) return;
        updateDiscoveredList();
    }

    @Override
    public void onEndpointLost(@NonNull String endpointId) {
        if (!isAdded()) return;
        updateDiscoveredList();
    }

    @Override
    public void onConnectionInitiated(@NonNull NearbyDevice device, @NonNull String authDigits) {
        if (!isAdded()) return;

        pendingApprovalEndpointId = device.getEndpointId();
        cardApproval.setVisibility(View.VISIBLE);
        tvApprovalDevice.setText(String.format("Peer: %s", device.getEndpointName()));
        tvAuthCode.setText(String.format(getString(R.string.nearby_auth_code_format), authDigits));
    }

    @Override
    public void onConnectionResult(@NonNull String endpointId, boolean isConnected, @NonNull String message) {
        if (!isAdded() || getContext() == null) return;

        cardApproval.setVisibility(View.GONE);
        pendingApprovalEndpointId = null;

        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        updateUiState();

        if (isConnected && relayEngine != null) {
            relayEngine.onPeerConnected(endpointId);
        }
    }

    @Override
    public void onDisconnected(@NonNull String endpointId) {
        if (!isAdded() || getContext() == null) return;

        if (relayEngine != null) {
            relayEngine.onPeerDisconnected(endpointId);
        }

        Toast.makeText(getContext(), R.string.nearby_radio_state_disconnected, Toast.LENGTH_SHORT).show();
        updateUiState();
    }

    @Override
    public void onPayloadReceived(@NonNull String endpointId, @NonNull byte[] payloadBytes) {
        if (!isAdded() || getContext() == null) return;

        // Delegate to RelayEngine to handle inventory, ACKs, rejections, or new SOS alerts
        relayEngine.handleIncomingPayload(endpointId, payloadBytes);
    }

    @Override
    public void onPayloadTransferUpdate(@NonNull String endpointId, long payloadId, int status) {
        // Can be used for fine-grained chunk progress if needed
    }

    @Override
    public void onError(@NonNull String errorMessage) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
        updateUiState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connectionManager != null) {
            connectionManager.setListener(null);
        }
        if (getContext() != null) {
            InternetGatewayService.getInstance(requireContext()).unregisterListener(gatewayConnectivityListener);
            PendingUploadManager.getInstance(requireContext()).unregisterSyncListener(gatewaySyncListener);
        }
    }
}
