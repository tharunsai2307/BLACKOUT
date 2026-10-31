package com.blackout.emergency.fragments;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.blackout.emergency.R;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.services.LocationService;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Locale;

/**
 * Dedicated Emergency SOS Beacon Fragment.
 * Implements real offline emergency SOS creation, genuine direct GPS location capture,
 * and permanent local Room database persistence.
 */
public class SosFragment extends Fragment {

    private ChipGroup chipGroupCategories;
    private TextView tvPriorityLevel;
    private TextView tvHeaderBadge;
    private ImageView ivPriorityIcon;
    private TextInputEditText etDescription;

    // Location UI elements
    private TextView tvLocationStatus;
    private TextView tvCoordinates;
    private TextView tvAccuracy;
    private TextView tvTimestamp;
    private ProgressBar pbLocation;
    private MaterialButton btnGetLocation;
    private ImageView ivLocationIcon;

    private String selectedCategory = Constants.CATEGORY_MEDICAL;
    private String calculatedPriority = Constants.PRIORITY_CRITICAL;
    private LocationService.LocationResult currentLocationResult = null;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                    startLocationCapture();
                } else {
                    updateLocationUiUnavailable(Constants.LOC_STATUS_DENIED);
                    showPermissionRationaleDialog();
                }
            });

    public static SosFragment newInstance() {
        return new SosFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView btnBack = view.findViewById(R.id.btn_sos_back);
        chipGroupCategories = view.findViewById(R.id.chip_group_categories);
        tvPriorityLevel = view.findViewById(R.id.tv_priority_level);
        tvHeaderBadge = view.findViewById(R.id.tv_sos_header_badge);
        ivPriorityIcon = view.findViewById(R.id.iv_priority_icon);
        etDescription = view.findViewById(R.id.et_sos_description);

        tvLocationStatus = view.findViewById(R.id.tv_sos_location_status);
        tvCoordinates = view.findViewById(R.id.tv_sos_coordinates);
        tvAccuracy = view.findViewById(R.id.tv_sos_accuracy);
        tvTimestamp = view.findViewById(R.id.tv_sos_timestamp);
        pbLocation = view.findViewById(R.id.pb_sos_location);
        btnGetLocation = view.findViewById(R.id.btn_sos_get_location);
        ivLocationIcon = view.findViewById(R.id.iv_sos_location_icon);

        MaterialButton btnCreateSos = view.findViewById(R.id.btn_sos_prepare);

        // Back Navigation
        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // 8 Emergency categories selection listener
        setupCategoryListener();

        // Location capture button listener
        btnGetLocation.setOnClickListener(v -> handleLocationButtonClick());

        // Create SOS Action
        btnCreateSos.setOnClickListener(v -> handleCreateSos());

        // If permission is already granted, attempt initial location capture
        if (getContext() != null && LocationService.getInstance(getContext()).hasLocationPermission()) {
            startLocationCapture();
        } else {
            updateLocationUiUnavailable(Constants.LOC_STATUS_UNAVAILABLE);
        }
    }

    private void setupCategoryListener() {
        chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_medical) {
                selectedCategory = Constants.CATEGORY_MEDICAL;
                calculatedPriority = Constants.PRIORITY_CRITICAL;
            } else if (checkedId == R.id.chip_accident) {
                selectedCategory = Constants.CATEGORY_ROAD_ACCIDENT;
                calculatedPriority = Constants.PRIORITY_HIGH;
            } else if (checkedId == R.id.chip_trapped) {
                selectedCategory = Constants.CATEGORY_TRAPPED;
                calculatedPriority = Constants.PRIORITY_HIGH;
            } else if (checkedId == R.id.chip_flood) {
                selectedCategory = Constants.CATEGORY_FLOOD;
                calculatedPriority = Constants.PRIORITY_HIGH;
            } else if (checkedId == R.id.chip_fire) {
                selectedCategory = Constants.CATEGORY_FIRE;
                calculatedPriority = Constants.PRIORITY_CRITICAL;
            } else if (checkedId == R.id.chip_disaster) {
                selectedCategory = Constants.CATEGORY_DISASTER;
                calculatedPriority = Constants.PRIORITY_CRITICAL;
            } else if (checkedId == R.id.chip_personal_safety) {
                selectedCategory = Constants.CATEGORY_PERSONAL_SAFETY;
                calculatedPriority = Constants.PRIORITY_HIGH;
            } else {
                selectedCategory = Constants.CATEGORY_OTHER;
                calculatedPriority = Constants.PRIORITY_MODERATE;
            }

            updatePriorityUi();
        });

        updatePriorityUi();
    }

    private void updatePriorityUi() {
        if (getContext() == null) {
            return;
        }

        if (Constants.PRIORITY_CRITICAL.equals(calculatedPriority)) {
            tvPriorityLevel.setText(R.string.sos_priority_critical);
            tvPriorityLevel.setTextColor(ContextCompat.getColor(getContext(), R.color.emergency_red));
            ivPriorityIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.emergency_red));
            tvHeaderBadge.setText(Constants.PRIORITY_CRITICAL);
            tvHeaderBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.emergency_red));
            tvHeaderBadge.setBackgroundResource(R.drawable.bg_badge_emergency);
        } else if (Constants.PRIORITY_HIGH.equals(calculatedPriority)) {
            tvPriorityLevel.setText(R.string.sos_priority_high);
            tvPriorityLevel.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
            ivPriorityIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.status_warning));
            tvHeaderBadge.setText(Constants.PRIORITY_HIGH);
            tvHeaderBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
            tvHeaderBadge.setBackgroundResource(R.drawable.bg_badge_amber);
        } else {
            tvPriorityLevel.setText(R.string.sos_priority_moderate);
            tvPriorityLevel.setTextColor(ContextCompat.getColor(getContext(), R.color.teal_accent));
            ivPriorityIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.teal_accent));
            tvHeaderBadge.setText(Constants.PRIORITY_MODERATE);
            tvHeaderBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.teal_accent));
            tvHeaderBadge.setBackgroundResource(R.drawable.bg_badge_teal);
        }
    }

    private void handleLocationButtonClick() {
        if (getContext() == null) {
            return;
        }

        LocationService locService = LocationService.getInstance(getContext());
        if (!locService.hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        if (!locService.isLocationServicesEnabled()) {
            showEnableLocationServicesDialog();
            return;
        }

        startLocationCapture();
    }

    private void startLocationCapture() {
        if (getContext() == null) {
            return;
        }

        pbLocation.setVisibility(View.VISIBLE);
        tvLocationStatus.setText(Constants.LOC_STATUS_GETTING);
        tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.teal_accent));
        ivLocationIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.teal_accent));
        btnGetLocation.setEnabled(false);

        LocationService.getInstance(getContext()).captureCurrentLocation(new LocationService.LocationCallback() {
            @Override
            public void onStatusUpdate(@NonNull String statusMessage) {
                if (!isAdded() || getContext() == null) return;
                tvLocationStatus.setText(statusMessage);
            }

            @Override
            public void onLocationResolved(@NonNull LocationService.LocationResult result) {
                if (!isAdded() || getContext() == null) return;

                currentLocationResult = result;
                pbLocation.setVisibility(View.GONE);
                btnGetLocation.setEnabled(true);
                btnGetLocation.setText(R.string.sos_btn_retry_location);

                if (result.hasCoordinates()) {
                    String coordsStr = String.format(Locale.getDefault(),
                            "Lat: %.5f° N, Lon: %.5f° E",
                            result.getLatitude(), result.getLongitude());
                    tvCoordinates.setText(coordsStr);

                    if (result.getAccuracy() != null) {
                        tvAccuracy.setVisibility(View.VISIBLE);
                        tvAccuracy.setText(String.format(Locale.getDefault(), "Accuracy: ±%.1f metres", result.getAccuracy()));
                    } else {
                        tvAccuracy.setVisibility(View.GONE);
                    }

                    if (result.getTimestamp() != null) {
                        tvTimestamp.setVisibility(View.VISIBLE);
                        tvTimestamp.setText(String.format(Locale.getDefault(), "Fix Time: %s",
                                DateTimeUtils.formatFullDateTime(result.getTimestamp())));
                    } else {
                        tvTimestamp.setVisibility(View.GONE);
                    }

                    if (Constants.LOC_STATUS_ACQUIRED.equals(result.getStatus())) {
                        tvLocationStatus.setText(Constants.LOC_STATUS_ACQUIRED);
                        tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_success));
                        ivLocationIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.status_success));
                    } else {
                        tvLocationStatus.setText(Constants.LOC_STATUS_CACHED);
                        tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
                        ivLocationIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.status_warning));
                    }
                } else {
                    updateLocationUiUnavailable(result.getStatus());
                }
            }
        });
    }

    private void updateLocationUiUnavailable(String status) {
        if (getContext() == null) return;

        pbLocation.setVisibility(View.GONE);
        btnGetLocation.setEnabled(true);
        tvLocationStatus.setText(status);
        tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
        ivLocationIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.status_warning));
        tvCoordinates.setText(R.string.sos_coords_none);
        tvAccuracy.setVisibility(View.GONE);
        tvTimestamp.setVisibility(View.GONE);
    }

    private void showPermissionRationaleDialog() {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Location Permission Required")
                .setMessage("GPS coordinates allow emergency rescue teams to locate you during an emergency. Coordinates are saved locally on this device and are not uploaded to any cloud server.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                })
                .setNegativeButton("Proceed Without GPS", null)
                .show();
    }

    private void showEnableLocationServicesDialog() {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Location Services Disabled")
                .setMessage("Device GPS hardware is turned off. Turn on location services in device Settings to acquire coordinates.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleCreateSos() {
        String inputDescription = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        final String finalDescription;
        if (TextUtils.isEmpty(inputDescription)) {
            finalDescription = "Emergency Assistance Requested: " + selectedCategory;
        } else {
            finalDescription = inputDescription;
        }

        // Build Confirmation Review Message
        StringBuilder summary = new StringBuilder();
        summary.append("Category: ").append(selectedCategory).append("\n");
        summary.append("Priority: ").append(calculatedPriority).append("\n\n");
        summary.append("Description:\n").append(finalDescription).append("\n\n");

        if (currentLocationResult != null && currentLocationResult.hasCoordinates()) {
            summary.append(String.format(Locale.getDefault(), "Coordinates: %.5f° N, %.5f° E\n",
                    currentLocationResult.getLatitude(), currentLocationResult.getLongitude()));
            if (currentLocationResult.getAccuracy() != null) {
                summary.append(String.format(Locale.getDefault(), "Accuracy: ±%.1f metres\n", currentLocationResult.getAccuracy()));
            }
            summary.append("Location Status: ").append(currentLocationResult.getStatus()).append("\n\n");
        } else {
            summary.append("Coordinates: Not Available\n");
            summary.append("Location Status: ").append(currentLocationResult != null ? currentLocationResult.getStatus() : Constants.LOC_STATUS_UNAVAILABLE).append("\n\n");
        }

        summary.append("NOTE: This emergency alert will be saved locally on this device. Offline peer-to-peer transmission will be active in future phases.");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sos_confirm_dialog_title)
                .setMessage(summary.toString())
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> saveSosAlert(finalDescription))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void saveSosAlert(String description) {
        if (getContext() == null) return;

        Double lat = null;
        Double lon = null;
        Float accuracy = null;
        Long locTimestamp = null;
        String locStatus = Constants.LOC_STATUS_UNAVAILABLE;
        String messageStatus = Constants.STATUS_LOCATION_UNAVAILABLE;

        if (currentLocationResult != null && currentLocationResult.hasCoordinates()) {
            lat = currentLocationResult.getLatitude();
            lon = currentLocationResult.getLongitude();
            accuracy = currentLocationResult.getAccuracy();
            locTimestamp = currentLocationResult.getTimestamp();
            locStatus = currentLocationResult.getStatus();
            messageStatus = Constants.STATUS_LOCAL_SAVED;
        }

        EmergencyMessage alert = new EmergencyMessage(
                "LOCAL_NODE_ID",
                Constants.DEFAULT_NODE_CALLSIGN,
                selectedCategory,
                description,
                calculatedPriority,
                lat,
                lon,
                accuracy,
                locTimestamp,
                messageStatus,
                locStatus
        );

        EmergencyRepository.getInstance(requireContext()).insertMessage(alert, new EmergencyRepository.Callback<Boolean>() {
            @Override
            public void onComplete(Boolean success) {
                if (!isAdded() || getContext() == null) return;

                if (Boolean.TRUE.equals(success)) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.sos_confirm_saved_title)
                            .setMessage(R.string.sos_confirm_saved_message)
                            .setPositiveButton(R.string.sos_dialog_positive, (d, w) -> {
                                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                                    getParentFragmentManager().popBackStack();
                                }
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    Toast.makeText(getContext(), "Failed to save emergency alert to local database.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            LocationService.getInstance(getContext()).cancelLocationCapture();
        }
    }
}
