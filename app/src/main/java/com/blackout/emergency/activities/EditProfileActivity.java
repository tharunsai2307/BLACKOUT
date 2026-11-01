package com.blackout.emergency.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.auth.SupabaseAuthClient;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.database.UserProfileEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Tactical profile editing activity.
 * Records identity, emergency contact phone, blood type, region, language, and medical notes.
 * Persists immediately to local Room database and synchronizes with Supabase if online.
 */
public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private TextInputEditText etBloodType;
    private TextInputEditText etCountry;
    private TextInputEditText etLanguage;
    private TextInputEditText etMedicalNotes;
    private MaterialButton btnSave;
    private ProgressBar pbLoading;

    private SessionManager sessionManager;
    private EmergencyRepository repository;
    private SupabaseAuthClient authClient;
    private UserProfileEntity currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        sessionManager = SessionManager.getInstance(this);
        repository = EmergencyRepository.getInstance(getApplicationContext());
        authClient = new SupabaseAuthClient(this);

        ImageButton btnBack = findViewById(R.id.btn_edit_profile_back);
        etName = findViewById(R.id.et_edit_name);
        etPhone = findViewById(R.id.et_edit_phone);
        etBloodType = findViewById(R.id.et_edit_blood_type);
        etCountry = findViewById(R.id.et_edit_country);
        etLanguage = findViewById(R.id.et_edit_language);
        etMedicalNotes = findViewById(R.id.et_edit_medical_notes);
        btnSave = findViewById(R.id.btn_edit_profile_save);
        pbLoading = findViewById(R.id.pb_edit_profile_loading);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> handleSaveProfile());

        loadExistingProfile();
    }

    private void loadExistingProfile() {
        String userId = sessionManager.getUserId();

        repository.getUserProfile(userId, profile -> {
            runOnUiThread(() -> {
                if (profile != null) {
                    currentProfile = profile;
                    if (!TextUtils.isEmpty(profile.fullName)) etName.setText(profile.fullName);
                    if (!TextUtils.isEmpty(profile.phone)) etPhone.setText(profile.phone);
                    if (!TextUtils.isEmpty(profile.bloodType)) etBloodType.setText(profile.bloodType);
                    if (!TextUtils.isEmpty(profile.country)) etCountry.setText(profile.country);
                    if (!TextUtils.isEmpty(profile.preferredLanguage)) etLanguage.setText(profile.preferredLanguage);
                    if (!TextUtils.isEmpty(profile.medicalNotes)) etMedicalNotes.setText(profile.medicalNotes);
                } else {
                    etName.setText(sessionManager.getFullName());
                }
            });
        });
    }

    private void handleSaveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String bloodType = etBloodType.getText() != null ? etBloodType.getText().toString().trim() : "";
        String country = etCountry.getText() != null ? etCountry.getText().toString().trim() : "";
        String language = etLanguage.getText() != null ? etLanguage.getText().toString().trim() : "";
        String medicalNotes = etMedicalNotes.getText() != null ? etMedicalNotes.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name cannot be empty");
            etName.requestFocus();
            return;
        }

        setLoading(true);

        String userId = sessionManager.getUserId();
        String email = sessionManager.getEmail();
        String role = sessionManager.getRole();

        UserProfileEntity profile = currentProfile != null ? currentProfile : new UserProfileEntity(userId, email, name, role);
        profile.fullName = name;
        profile.phone = phone;
        profile.bloodType = bloodType;
        profile.country = country;
        profile.preferredLanguage = language;
        profile.medicalNotes = medicalNotes;
        profile.updatedAt = System.currentTimeMillis();

        // 1. Persist immediately to Room
        repository.saveUserProfile(profile);
        sessionManager.setUserDetails(userId, email, name, role);

        // 2. Sync to Supabase if authenticated and online
        if (sessionManager.isLoggedIn()) {
            String token = sessionManager.getAccessToken();
            org.json.JSONObject profileJson = new org.json.JSONObject();
            try {
                profileJson.put("full_name", name);
                profileJson.put("phone_number", phone);
                profileJson.put("medical_notes", medicalNotes);
            } catch (Exception ignored) {}

            authClient.updateUserProfile(userId, profileJson, token != null ? token : "", new SupabaseAuthClient.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(EditProfileActivity.this, "Profile updated and synchronized", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onFailure(@androidx.annotation.NonNull String errorMessage) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(EditProfileActivity.this, "Saved offline in Room (Cloud sync pending)", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } else {
            setLoading(false);
            Toast.makeText(this, "Profile saved offline", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setLoading(boolean loading) {
        if (loading) {
            pbLoading.setVisibility(View.VISIBLE);
            btnSave.setEnabled(false);
            btnSave.setText("");
        } else {
            pbLoading.setVisibility(View.GONE);
            btnSave.setEnabled(true);
            btnSave.setText("SAVE PROFILE");
        }
    }
}
