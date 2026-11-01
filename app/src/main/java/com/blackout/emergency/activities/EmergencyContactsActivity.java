package com.blackout.emergency.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.adapters.EmergencyContactAdapter;
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.database.EmergencyContactEntity;
import com.blackout.emergency.database.EmergencyRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tactical Emergency Contacts (ICE) Management Activity.
 * Allows citizen responders to view, add, and remove trusted emergency contacts offline.
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private LinearLayout layoutEmpty;
    private RecyclerView rvContacts;
    private MaterialButton btnAdd;
    private ImageButton btnBack;

    private EmergencyContactAdapter adapter;
    private EmergencyRepository repository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        repository = EmergencyRepository.getInstance(getApplicationContext());
        sessionManager = SessionManager.getInstance(this);

        btnBack = findViewById(R.id.btn_contacts_back);
        layoutEmpty = findViewById(R.id.layout_contacts_empty);
        rvContacts = findViewById(R.id.rv_contacts);
        btnAdd = findViewById(R.id.btn_contacts_add);

        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> showAddContactDialog());

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmergencyContactAdapter(new EmergencyContactAdapter.OnContactActionListener() {
            @Override
            public void onEdit(EmergencyContactEntity contact) {
                // Not editable in simple mode
            }

            @Override
            public void onDelete(EmergencyContactEntity contact) {
                deleteContact(contact);
            }
        });
        rvContacts.setAdapter(adapter);

        loadContacts();
    }

    private void loadContacts() {
        String userId = sessionManager.getUserId();
        if (TextUtils.isEmpty(userId)) {
            userId = "guest-user";
        }

        repository.getEmergencyContacts(userId, contacts -> runOnUiThread(() -> {
            if (contacts != null && !contacts.isEmpty()) {
                adapter.setContacts(contacts);
                layoutEmpty.setVisibility(View.GONE);
                rvContacts.setVisibility(View.VISIBLE);
            } else {
                adapter.setContacts(new ArrayList<>());
                layoutEmpty.setVisibility(View.VISIBLE);
                rvContacts.setVisibility(View.GONE);
            }
        }));
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_dialog_contact_name);
        TextInputEditText etPhone = dialogView.findViewById(R.id.et_dialog_contact_phone);
        TextInputEditText etRelationship = dialogView.findViewById(R.id.et_dialog_contact_relationship);
        CheckBox cbPrimary = dialogView.findViewById(R.id.cb_dialog_contact_primary);

        new AlertDialog.Builder(this)
                .setTitle("Add Emergency Contact")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
                    String phone = etPhone != null && etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
                    String rel = etRelationship != null && etRelationship.getText() != null ? etRelationship.getText().toString().trim() : "";
                    boolean isPrimary = cbPrimary != null && cbPrimary.isChecked();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone)) {
                        Toast.makeText(this, "Name and Phone Number are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String userId = sessionManager.getUserId();
                    if (TextUtils.isEmpty(userId)) {
                        userId = "guest-user";
                    }

                    EmergencyContactEntity contact = new EmergencyContactEntity(
                            UUID.randomUUID().toString(),
                            userId,
                            name,
                            phone,
                            rel,
                            isPrimary,
                            "LOCAL",
                            System.currentTimeMillis()
                    );

                    repository.saveEmergencyContact(contact, success -> runOnUiThread(this::loadContacts));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteContact(EmergencyContactEntity contact) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage("Remove " + contact.getContactName() + " from your emergency contacts?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    repository.deleteEmergencyContact(contact.getId(), success -> runOnUiThread(this::loadContacts));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
