package com.blackout.emergency.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.blackout.emergency.R;
import com.blackout.emergency.activities.EditProfileActivity;
import com.blackout.emergency.activities.EmergencyContactsActivity;
import com.blackout.emergency.activities.RegisterActivity;
import com.blackout.emergency.activities.WelcomeActivity;
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.auth.SupabaseAuthClient;
import com.blackout.emergency.database.EmergencyContactEntity;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.database.UserProfileEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

/**
 * Tactical Profile Fragment: Manages user identity, emergency contacts (ICE),
 * personal/medical details, mesh telemetry, and session state.
 */
public class ProfileFragment extends Fragment {

    private TextView tvAvatar;
    private TextView tvName;
    private TextView tvEmail;
    private TextView tvRoleBadge;
    private TextView tvCompletionPercentage;
    private ProgressBar pbCompletion;
    private TextView tvContactsCount;
    private TextView tvMedicalSummary;
    private LinearLayout layoutMedicalDetails;
    private TextView tvBloodTypeDisplay;
    private TextView tvMedicalNotesDisplay;
    private MaterialButton btnPrimaryAction;
    private ImageView ivSyncStatus;

    private SessionManager sessionManager;
    private EmergencyRepository repository;
    private SupabaseAuthClient authClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = SessionManager.getInstance(requireContext());
        repository = EmergencyRepository.getInstance(requireContext().getApplicationContext());
        authClient = new SupabaseAuthClient(requireContext());

        tvAvatar = view.findViewById(R.id.tv_profile_avatar);
        tvName = view.findViewById(R.id.tv_profile_name);
        tvEmail = view.findViewById(R.id.tv_profile_email);
        tvRoleBadge = view.findViewById(R.id.tv_profile_role_badge);
        tvCompletionPercentage = view.findViewById(R.id.tv_completion_percentage);
        pbCompletion = view.findViewById(R.id.pb_profile_completion);
        tvContactsCount = view.findViewById(R.id.tv_contacts_count);
        tvMedicalSummary = view.findViewById(R.id.tv_medical_summary);
        layoutMedicalDetails = view.findViewById(R.id.layout_medical_details);
        tvBloodTypeDisplay = view.findViewById(R.id.tv_blood_type_display);
        tvMedicalNotesDisplay = view.findViewById(R.id.tv_medical_notes_display);
        btnPrimaryAction = view.findViewById(R.id.btn_profile_action_primary);
        ivSyncStatus = view.findViewById(R.id.iv_profile_sync_status);

        MaterialCardView cardContacts = view.findViewById(R.id.card_emergency_contacts);
        MaterialCardView cardMedical = view.findViewById(R.id.card_medical_info);
        MaterialCardView cardMesh = view.findViewById(R.id.card_mesh_diagnostics);

        cardContacts.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EmergencyContactsActivity.class);
            startActivity(intent);
        });

        cardMedical.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            startActivity(intent);
        });

        cardMesh.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragment_container, new NearbyFragment())
                    .addToBackStack(null)
                    .commit();
        });

        ivSyncStatus.setOnClickListener(v -> triggerCloudSync());

        setupSessionAndActions();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileData();
    }

    private void setupSessionAndActions() {
        if (sessionManager.isLoggedIn()) {
            btnPrimaryAction.setText("LOG OUT");
            btnPrimaryAction.setOnClickListener(v -> confirmLogout());
        } else {
            btnPrimaryAction.setText("CREATE / LINK ACCOUNT");
            btnPrimaryAction.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), RegisterActivity.class);
                startActivity(intent);
            });
        }
    }

    private void loadProfileData() {
        String userId = sessionManager.getUserId();
        boolean isLoggedIn = sessionManager.isLoggedIn();

        if (isLoggedIn) {
            String fullName = sessionManager.getFullName();
            String email = sessionManager.getEmail();
            String role = sessionManager.getRole();

            tvName.setText(!TextUtils.isEmpty(fullName) ? fullName : "Responder");
            tvEmail.setText(email);
            tvRoleBadge.setText(role.toUpperCase());
            tvAvatar.setText(computeInitials(fullName));
        } else {
            tvName.setText("Guest Responder");
            tvEmail.setText("Offline Guest Mode");
            tvRoleBadge.setText("GUEST");
            tvAvatar.setText("GU");
        }

        // Asynchronously load local Room profile and contacts
        repository.getUserProfile(userId, profile -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                int completionScore = 20; // Base score for having an identity

                if (profile != null) {
                    if (!TextUtils.isEmpty(profile.fullName)) {
                        tvName.setText(profile.fullName);
                        tvAvatar.setText(computeInitials(profile.fullName));
                    }
                    if (!TextUtils.isEmpty(profile.phone)) {
                        completionScore += 20;
                    }

                    boolean hasMedical = !TextUtils.isEmpty(profile.bloodType) || !TextUtils.isEmpty(profile.medicalNotes);
                    if (hasMedical) {
                        completionScore += 30;
                        layoutMedicalDetails.setVisibility(View.VISIBLE);
                        tvBloodTypeDisplay.setText("Blood Type: " + (!TextUtils.isEmpty(profile.bloodType) ? profile.bloodType : "Not set"));
                        tvMedicalNotesDisplay.setText("Medical Notes: " + (!TextUtils.isEmpty(profile.medicalNotes) ? profile.medicalNotes : "None recorded"));
                        tvMedicalSummary.setText("Blood Type: " + (!TextUtils.isEmpty(profile.bloodType) ? profile.bloodType : "Recorded"));
                    } else {
                        layoutMedicalDetails.setVisibility(View.GONE);
                        tvMedicalSummary.setText("Tap to record blood type, allergies, and responder notes");
                    }
                }

                // Check emergency contacts count
                final int finalCompletionScore = completionScore;
                repository.getEmergencyContacts(userId, contacts -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        int count = contacts != null ? contacts.size() : 0;
                        tvContactsCount.setText(count + " Contact" + (count == 1 ? "" : "s") + " Configured");

                        int totalScore = finalCompletionScore;
                        if (count > 0) {
                            totalScore += 30;
                        }
                        if (totalScore > 100) totalScore = 100;

                        pbCompletion.setProgress(totalScore);
                        tvCompletionPercentage.setText(totalScore + "%");
                    });
                });
            });
        });
    }

    private void triggerCloudSync() {
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Sign in to synchronize profile with Central Operations", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Synchronizing profile with secure cloud gateway...", Toast.LENGTH_SHORT).show();

        String token = sessionManager.getAccessToken();
        String userId = sessionManager.getUserId();

        authClient.fetchUserProfile(userId != null ? userId : "", token != null ? token : "", new SupabaseAuthClient.ProfileCallback() {
            @Override
            public void onSuccess(@NonNull org.json.JSONObject profileJson) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        loadProfileData();
                        Toast.makeText(requireContext(), "Profile synchronized successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(@NonNull String errorMessage) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Cloud sync unreachable. Using offline profile.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out? Local emergency SOS and mesh functionality will remain available in Guest mode.")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    sessionManager.logout();
                    sessionManager.setGuestMode(true);
                    Intent intent = new Intent(requireContext(), WelcomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String computeInitials(String name) {
        if (TextUtils.isEmpty(name)) return "GU";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else if (name.length() >= 2) {
            return name.substring(0, 2).toUpperCase();
        }
        return name.toUpperCase();
    }
}
