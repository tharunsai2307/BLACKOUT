package com.blackout.emergency.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.database.EmergencyContactEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for managing and displaying emergency contacts (ICE).
 */
public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    public interface OnContactActionListener {
        void onEdit(EmergencyContactEntity contact);
        void onDelete(EmergencyContactEntity contact);
    }

    private final List<EmergencyContactEntity> contacts = new ArrayList<>();
    private final OnContactActionListener listener;

    public EmergencyContactAdapter(OnContactActionListener listener) {
        this.listener = listener;
    }

    public void setContacts(List<EmergencyContactEntity> newContacts) {
        this.contacts.clear();
        if (newContacts != null) {
            this.contacts.addAll(newContacts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyContactEntity contact = contacts.get(position);

        holder.tvName.setText(contact.contactName);
        holder.tvPhone.setText(contact.contactPhone);
        holder.tvRelationship.setText("Relationship: " + (contact.relationship != null ? contact.relationship : "Contact"));
        holder.tvPrimaryBadge.setVisibility(contact.isPrimary ? View.VISIBLE : View.GONE);

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(contact);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(contact);
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvPhone;
        TextView tvRelationship;
        TextView tvPrimaryBadge;
        ImageButton btnEdit;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_contact_name);
            tvPhone = itemView.findViewById(R.id.tv_item_contact_phone);
            tvRelationship = itemView.findViewById(R.id.tv_item_contact_relationship);
            tvPrimaryBadge = itemView.findViewById(R.id.tv_item_contact_primary_badge);
            btnEdit = itemView.findViewById(R.id.btn_item_contact_edit);
            btnDelete = itemView.findViewById(R.id.btn_item_contact_delete);
        }
    }
}

