package com.blackout.emergency.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.blackout.emergency.R;
import com.blackout.emergency.fragments.AlertsFragment;
import com.blackout.emergency.fragments.HistoryFragment;
import com.blackout.emergency.fragments.HomeFragment;
import com.blackout.emergency.fragments.NearbyFragment;
import com.blackout.emergency.fragments.ProfileFragment;
import com.blackout.emergency.fragments.SosFragment;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.services.MeshForegroundService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main Activity: Primary container for the BLACKOUT emergency platform.
 * Tabs: Home, SOS, Alerts, History, Profile.
 * Manages fragment lifecycle, tab transitions, back navigation stack,
 * Bluetooth readiness checks, and Autonomous Mesh Foreground Service coordination (Phase 12).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String KEY_CURRENT_TAB = "key_current_selected_tab";
    private static final String TAG_HOME = "tag_home";
    private static final String TAG_SOS = "tag_sos";
    private static final String TAG_ALERTS = "tag_alerts";
    private static final String TAG_HISTORY = "tag_history";
    private static final String TAG_PROFILE = "tag_profile";
    private static final String TAG_NEARBY = "tag_nearby";

    private BottomNavigationView bottomNavigationView;
    private int currentTabId = R.id.navigation_home;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<Intent> requestBluetoothLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Internet Gateway Service to monitor connectivity and sync pending alerts
        InternetGatewayService.getInstance(this);
        com.blackout.emergency.warnings.sync.WarningSyncWorker.schedulePeriodicSync(this);

        initActivityResultLaunchers();

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Setup bottom navigation listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentTabId) {
                return true;
            }
            switchTab(itemId);
            return true;
        });

        // Setup custom back navigation handling
        setupBackNavigation();

        // Restore state or initialize default landing screen
        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(KEY_CURRENT_TAB, R.id.navigation_home);
            bottomNavigationView.setSelectedItemId(currentTabId);
        } else {
            if (getIntent() != null && "ALERTS".equals(getIntent().getStringExtra("navigate_to_tab"))) {
                switchTab(R.id.navigation_alerts);
                bottomNavigationView.setSelectedItemId(R.id.navigation_alerts);
            } else if (getIntent() != null && "SOS".equals(getIntent().getStringExtra("navigate_to_tab"))) {
                switchTab(R.id.navigation_sos);
                bottomNavigationView.setSelectedItemId(R.id.navigation_sos);
            } else {
                switchTab(R.id.navigation_home);
            }
        }

        // Initialize autonomous mesh networking
        checkPermissionsAndStartMeshService();
    }

    private void initActivityResultLaunchers() {
        requestBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (NearbyConnectionManager.isBluetoothEnabled()) {
                        startMeshForegroundService();
                    } else {
                        Log.w(TAG, "Bluetooth was not enabled by user; mesh discovery paused");
                    }
                });

        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                resultMap -> {
                    if (NearbyConnectionManager.hasRequiredPermissions(this)) {
                        checkBluetoothAndStartMeshService();
                    } else {
                        Log.w(TAG, "Nearby/Location permissions denied; autonomous mesh cannot start");
                    }
                });
    }

    private void checkPermissionsAndStartMeshService() {
        if (!NearbyConnectionManager.hasRequiredPermissions(this)) {
            requestPermissionsLauncher.launch(NearbyConnectionManager.getRequiredPermissions());
        } else {
            checkBluetoothAndStartMeshService();
        }
    }

    private void checkBluetoothAndStartMeshService() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth hardware not detected on this device");
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestBluetoothLauncher.launch(enableBtIntent);
        } else {
            startMeshForegroundService();
        }
    }

    private void startMeshForegroundService() {
        try {
            MeshForegroundService.start(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MeshForegroundService", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshForegroundService.setAppInForeground(true);
        if (NearbyConnectionManager.hasRequiredPermissions(this)
                && NearbyConnectionManager.isBluetoothEnabled()
                && !MeshForegroundService.isRunning()) {
            startMeshForegroundService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshForegroundService.setAppInForeground(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_TAB, currentTabId);
    }

    /**
     * Switches the active fragment based on the selected bottom navigation tab.
     */
    private void switchTab(int tabId) {
        currentTabId = tabId;

        // Clear any back stack when user explicitly chooses a bottom tab
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Fragment fragment;
        String tag;

        if (tabId == R.id.navigation_sos) {
            fragment = new SosFragment();
            tag = TAG_SOS;
        } else if (tabId == R.id.navigation_alerts) {
            fragment = new AlertsFragment();
            tag = TAG_ALERTS;
        } else if (tabId == R.id.navigation_history) {
            fragment = new HistoryFragment();
            tag = TAG_HISTORY;
        } else if (tabId == R.id.navigation_profile) {
            fragment = new ProfileFragment();
            tag = TAG_PROFILE;
        } else {
            fragment = new HomeFragment();
            tag = TAG_HOME;
        }

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    /**
     * Programmatically switches the selected tab from child fragments.
     */
    public void selectTab(int menuId) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(menuId);
        }
    }

    /**
     * Navigates directly to the dedicated Emergency SOS Beacon tab.
     */
    public void navigateToSos() {
        selectTab(R.id.navigation_sos);
    }

    /**
     * Opens the Mesh Radar & Peer Peering fragment from Home or Profile.
     */
    public void openMeshRadar() {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, new NearbyFragment(), TAG_NEARBY)
                .addToBackStack(TAG_NEARBY)
                .commit();
    }

    /**
     * Configures Android back navigation:
     * 1. If on back stack (e.g. Mesh Radar), pop it.
     * 2. If on a secondary tab, return to Home.
     * 3. If on Home, exit the application.
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else if (currentTabId != R.id.navigation_home) {
                    selectTab(R.id.navigation_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
}
