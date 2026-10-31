package com.blackout.emergency.activities;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.blackout.emergency.R;
import com.blackout.emergency.fragments.AlertsFragment;
import com.blackout.emergency.fragments.HistoryFragment;
import com.blackout.emergency.fragments.HomeFragment;
import com.blackout.emergency.fragments.NearbyFragment;
import com.blackout.emergency.fragments.SettingsFragment;
import com.blackout.emergency.fragments.SosFragment;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main Activity: Primary container for the BLACKOUT emergency communication application.
 * Manages fragment lifecycle, tab transitions, back navigation stack, and SOS beacon navigation.
 */
public class MainActivity extends AppCompatActivity {

    private static final String KEY_CURRENT_TAB = "key_current_selected_tab";
    private static final String TAG_HOME = "tag_home";
    private static final String TAG_NEARBY = "tag_nearby";
    private static final String TAG_ALERTS = "tag_alerts";
    private static final String TAG_HISTORY = "tag_history";
    private static final String TAG_SETTINGS = "tag_settings";
    private static final String TAG_SOS = "tag_sos";

    private BottomNavigationView bottomNavigationView;
    private int currentTabId = R.id.navigation_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Internet Gateway Service to monitor connectivity and sync pending alerts
        InternetGatewayService.getInstance(this);
        com.blackout.emergency.warnings.sync.WarningSyncWorker.schedulePeriodicSync(this);

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
            } else {
                switchTab(R.id.navigation_home);
            }
        }
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

        // Clear any back stack (like an open SOS screen) when user explicitly chooses a bottom tab
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Fragment fragment;
        String tag;

        if (tabId == R.id.navigation_nearby) {
            fragment = new NearbyFragment();
            tag = TAG_NEARBY;
        } else if (tabId == R.id.navigation_alerts) {
            fragment = new AlertsFragment();
            tag = TAG_ALERTS;
        } else if (tabId == R.id.navigation_history) {
            fragment = new HistoryFragment();
            tag = TAG_HISTORY;
        } else if (tabId == R.id.navigation_settings) {
            fragment = new SettingsFragment();
            tag = TAG_SETTINGS;
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
     * Navigates directly to the dedicated Emergency SOS Beacon fragment.
     */
    public void navigateToSos() {
        SosFragment sosFragment = new SosFragment();

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out_right)
                .replace(R.id.fragment_container, sosFragment, TAG_SOS)
                .addToBackStack(TAG_SOS)
                .commit();
    }

    /**
     * Configures Android back navigation:
     * 1. If on back stack (e.g., SOS screen), pop it.
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
