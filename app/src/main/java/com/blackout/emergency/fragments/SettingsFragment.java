package com.blackout.emergency.fragments;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.blackout.emergency.R;
import com.blackout.emergency.services.LocationService;
import com.google.android.material.button.MaterialButton;

/**
 * Settings & Device Information Fragment.
 * Manages local node identity inspection, live runtime permission verification,
 * notification preferences, security model overview, and protocol details.
 */
public class SettingsFragment extends Fragment {

    private TextView tvLocationStatus;
    private MaterialButton btnRequestLocation;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> updateLocationPermissionUi());

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvLocationStatus = view.findViewById(R.id.tv_settings_location_status);
        btnRequestLocation = view.findViewById(R.id.btn_settings_request_location);

        // Location permission request button
        btnRequestLocation.setOnClickListener(v -> locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }));

        // Update live permission status on launch
        updateLocationPermissionUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLocationPermissionUi();
    }

    private void updateLocationPermissionUi() {
        if (getContext() == null || tvLocationStatus == null) {
            return;
        }

        boolean isGranted = LocationService.getInstance(getContext()).hasLocationPermission();
        if (isGranted) {
            tvLocationStatus.setText(R.string.settings_perm_granted);
            tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_success));
            tvLocationStatus.setBackgroundResource(R.drawable.bg_badge_green);
            btnRequestLocation.setEnabled(false);
            btnRequestLocation.setText(R.string.settings_perm_granted_btn);
        } else {
            tvLocationStatus.setText(R.string.settings_perm_denied);
            tvLocationStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.status_warning));
            tvLocationStatus.setBackgroundResource(R.drawable.bg_badge_amber);
            btnRequestLocation.setEnabled(true);
            btnRequestLocation.setText(R.string.settings_btn_request_perm);
        }
    }
}
