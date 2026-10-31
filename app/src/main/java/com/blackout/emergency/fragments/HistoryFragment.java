package com.blackout.emergency.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.adapters.MessageAdapter;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Message History Fragment: Displays persistent emergency SOS records from the local Room database.
 * Supports category filtering, inspecting full alert details, and persistent storage across app restarts.
 */
public class HistoryFragment extends Fragment {

    private RecyclerView recyclerMessages;
    private View layoutEmptyState;
    private MessageAdapter messageAdapter;
    private ChipGroup chipGroupFilter;
    private String currentSelectedCategory = null; // null represents "All"

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerMessages = view.findViewById(R.id.recycler_messages);
        layoutEmptyState = view.findViewById(R.id.layout_history_empty);
        chipGroupFilter = view.findViewById(R.id.chip_group_history_filter);

        // RecyclerView setup
        recyclerMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        messageAdapter = new MessageAdapter();
        messageAdapter.setOnMessageClickListener(this::showAlertDetailsDialog);
        recyclerMessages.setAdapter(messageAdapter);

        // Setup Category Filter Chips
        setupFilterChips();

        // Load messages from Room database
        loadMessages();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMessages();
    }

    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentSelectedCategory = null;
                loadMessages();
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_filter_all) {
                currentSelectedCategory = null;
            } else if (checkedId == R.id.chip_filter_medical) {
                currentSelectedCategory = Constants.CATEGORY_MEDICAL;
            } else if (checkedId == R.id.chip_filter_accident) {
                currentSelectedCategory = Constants.CATEGORY_ROAD_ACCIDENT;
            } else if (checkedId == R.id.chip_filter_trapped) {
                currentSelectedCategory = Constants.CATEGORY_TRAPPED;
            } else if (checkedId == R.id.chip_filter_flood) {
                currentSelectedCategory = Constants.CATEGORY_FLOOD;
            } else if (checkedId == R.id.chip_filter_fire) {
                currentSelectedCategory = Constants.CATEGORY_FIRE;
            } else if (checkedId == R.id.chip_filter_disaster) {
                currentSelectedCategory = Constants.CATEGORY_DISASTER;
            } else if (checkedId == R.id.chip_filter_personal_safety) {
                currentSelectedCategory = Constants.CATEGORY_PERSONAL_SAFETY;
            } else if (checkedId == R.id.chip_filter_other) {
                currentSelectedCategory = Constants.CATEGORY_OTHER;
            } else {
                currentSelectedCategory = null;
            }

            loadMessages();
        });
    }

    private void loadMessages() {
        if (getContext() == null) {
            return;
        }

        EmergencyRepository repo = EmergencyRepository.getInstance(requireContext());
        if (currentSelectedCategory == null) {
            repo.getAllMessages(this::displayMessages);
        } else {
            repo.getMessagesByCategory(currentSelectedCategory, this::displayMessages);
        }
    }

    private void displayMessages(List<EmergencyMessage> messages) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (messages == null || messages.isEmpty()) {
            recyclerMessages.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            messageAdapter.setMessages(new ArrayList<>());
        } else {
            recyclerMessages.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            messageAdapter.setMessages(messages);
        }
    }

    private void showAlertDetailsDialog(EmergencyMessage message) {
        if (getContext() == null || message == null) {
            return;
        }

        final android.content.Context ctx = requireContext();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            com.blackout.emergency.database.PendingUploadEntity uploadEntity = null;
            try {
                uploadEntity = com.blackout.emergency.database.BlackoutDatabase.getInstance(ctx)
                        .pendingUploadDao().getByMessageId(message.getMessageId());
            } catch (Exception ignored) {}

            final com.blackout.emergency.database.PendingUploadEntity finalUpload = uploadEntity;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getContext() == null) return;
                    showPopulatedDialog(message, finalUpload);
                });
            }
        });
    }

    private void showPopulatedDialog(EmergencyMessage message, @Nullable com.blackout.emergency.database.PendingUploadEntity upload) {
        if (getContext() == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(message.getEmergencyCategory()).append("\n");
        sb.append("Priority: ").append(message.getPriority()).append("\n");
        sb.append("Status: ").append(message.getStatus()).append("\n");
        sb.append("Recorded: ").append(DateTimeUtils.formatFullDateTime(message.getCreatedAt())).append("\n");
        sb.append("Expires: ").append(DateTimeUtils.formatFullDateTime(message.getExpiresAt())).append("\n");
        sb.append("Hop Count: ").append(message.getHopCount()).append(" / ").append(com.blackout.emergency.relay.RelayPolicy.DEFAULT_MAX_HOPS).append("\n");
        sb.append("Originating Device: ").append(message.getSenderDeviceId() != null ? message.getSenderDeviceId() : "LOCAL").append("\n");
        sb.append("Originating Callsign: ").append(message.getSenderCallsign() != null ? message.getSenderCallsign() : "NODE-LOCAL").append("\n\n");

        sb.append("CLOUD GATEWAY STATUS:\n");
        if (upload != null) {
            sb.append("Upload Status: ").append(upload.getUploadStatus()).append("\n");
            if (upload.getUploadedAt() > 0) {
                sb.append("Uploaded At: ").append(DateTimeUtils.formatFullDateTime(upload.getUploadedAt())).append("\n");
            }
            if (upload.getBackendReference() != null && !upload.getBackendReference().isEmpty()) {
                sb.append("Backend Ref: ").append(upload.getBackendReference()).append("\n");
            }
            if (upload.getLastErrorCode() != null && !upload.getLastErrorCode().isEmpty()) {
                sb.append("Last Error: ").append(upload.getLastErrorCode()).append("\n");
            }
        } else {
            sb.append("Upload Status: NOT QUEUED\n");
        }
        sb.append("\n");

        sb.append("LOCATION DATA:\n");
        if (message.hasCoordinates()) {
            sb.append(String.format(Locale.getDefault(), "Coordinates: %.5f° N, %.5f° E\n",
                    message.getLatitude(), message.getLongitude()));
            if (message.getLocationAccuracy() != null) {
                sb.append(String.format(Locale.getDefault(), "Accuracy: ±%.1f metres\n", message.getLocationAccuracy()));
            }
            if (message.getLocationTimestamp() != null) {
                sb.append("Fix Time: ").append(DateTimeUtils.formatFullDateTime(message.getLocationTimestamp())).append("\n");
            }
            sb.append("Status: ").append(message.getLocationStatus() != null ? message.getLocationStatus() : "Acquired").append("\n\n");
        } else {
            sb.append("Coordinates: Not Available\n");
            sb.append("Status: ").append(message.getLocationStatus() != null ? message.getLocationStatus() : Constants.LOC_STATUS_UNAVAILABLE).append("\n\n");
        }

        sb.append("EMERGENCY DESCRIPTION:\n");
        sb.append(message.getDescription()).append("\n\n");

        sb.append("Message ID: ").append(message.getMessageId());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_dialog_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.action_close, null)
                .setNeutralButton("Delete Alert", (dialog, which) -> confirmDeleteMessage(message));

        if (upload != null && !Constants.UPLOAD_STATUS_BACKEND_ACCEPTED.equals(upload.getUploadStatus())
                && !Constants.UPLOAD_STATUS_DUPLICATE_ACCEPTED.equals(upload.getUploadStatus())) {
            builder.setNegativeButton("Sync to Cloud", (dialog, which) -> {
                com.blackout.emergency.gateway.PendingUploadManager.getInstance(requireContext())
                        .flushPendingUploads(result -> {
                            if (isAdded() && getContext() != null) {
                                Toast.makeText(getContext(), "Cloud sync triggered.", Toast.LENGTH_SHORT).show();
                            }
                        });
            });
        }

        builder.show();
    }

    private void confirmDeleteMessage(EmergencyMessage message) {
        if (getContext() == null) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Emergency Alert")
                .setMessage("Are you sure you want to permanently remove this emergency record from your offline database?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    EmergencyRepository.getInstance(requireContext()).deleteMessage(message.getMessageId(), success -> {
                        if (!isAdded()) return;
                        if (Boolean.TRUE.equals(success)) {
                            Toast.makeText(getContext(), "Alert record deleted.", Toast.LENGTH_SHORT).show();
                            loadMessages();
                        } else {
                            Toast.makeText(getContext(), "Failed to delete alert record.", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
