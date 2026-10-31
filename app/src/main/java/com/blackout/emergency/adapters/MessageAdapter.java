package com.blackout.emergency.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.utils.DateTimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying emergency SOS messages saved in the offline Room database.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<EmergencyMessage> messageList = new ArrayList<>();
    private OnMessageClickListener clickListener;

    public interface OnMessageClickListener {
        void onMessageClick(EmergencyMessage message);
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.clickListener = listener;
    }

    public void setMessages(List<EmergencyMessage> newMessages) {
        this.messageList.clear();
        if (newMessages != null) {
            this.messageList.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(EmergencyMessage message) {
        if (message != null) {
            this.messageList.add(0, message);
            notifyItemInserted(0);
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        EmergencyMessage message = messageList.get(position);
        holder.bind(message, clickListener);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvPriorityBadge;
        private final TextView tvCategoryBadge;
        private final TextView tvStatusBadge;
        private final TextView tvMessageBody;
        private final LinearLayout layoutLocation;
        private final ImageView ivLocationIcon;
        private final TextView tvMessageLocation;
        private final TextView tvMessageTime;
        private final TextView tvSenderCallsign;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPriorityBadge = itemView.findViewById(R.id.tv_priority_badge);
            tvCategoryBadge = itemView.findViewById(R.id.tv_category_badge);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvMessageBody = itemView.findViewById(R.id.tv_message_body);
            layoutLocation = itemView.findViewById(R.id.layout_message_location);
            ivLocationIcon = itemView.findViewById(R.id.iv_message_location_icon);
            tvMessageLocation = itemView.findViewById(R.id.tv_message_location);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
            tvSenderCallsign = itemView.findViewById(R.id.tv_sender_callsign);
        }

        public void bind(final EmergencyMessage message, final OnMessageClickListener listener) {
            Context context = itemView.getContext();

            // Priority Badge
            String priority = message.getPriority() != null ? message.getPriority() : Constants.PRIORITY_MODERATE;
            tvPriorityBadge.setText(priority.toUpperCase(Locale.ROOT));

            if (Constants.PRIORITY_CRITICAL.equalsIgnoreCase(priority)) {
                tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.emergency_red));
                tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_emergency);
            } else if (Constants.PRIORITY_HIGH.equalsIgnoreCase(priority)) {
                tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            } else {
                tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            }

            // Category Badge
            String category = message.getCategory() != null ? message.getCategory() : "EMERGENCY";
            tvCategoryBadge.setText(category.toUpperCase(Locale.ROOT));

            // Status Badge
            String status = message.getStatus() != null ? message.getStatus() : Constants.STATUS_LOCAL_SAVED;

            if (Constants.STATUS_RELAYED.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("RELAYED");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            } else if (Constants.STATUS_RECEIVED.equalsIgnoreCase(status)) {
                String hopText = message.getHopCount() > 0
                        ? String.format(Locale.ROOT, "RELAYED (HOP %d)", message.getHopCount())
                        : "RECEIVED (P2P)";
                tvStatusBadge.setText(hopText);
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            } else if (Constants.STATUS_TRANSFERRED.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("TRANSFERRED");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_success));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
            } else if (Constants.STATUS_TRANSFER_FAILED.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("TRANSFER FAILED");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.emergency_red));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_emergency);
            } else if (Constants.STATUS_MAX_HOPS_REACHED.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("MAX HOPS REACHED");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
            } else if (Constants.STATUS_TRANSFER_IN_PROGRESS.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("SENDING...");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            } else if (Constants.STATUS_LOCAL_SAVED.equalsIgnoreCase(status) || Constants.STATUS_LOCAL_ONLY.equalsIgnoreCase(status)) {
                tvStatusBadge.setText(status.toUpperCase(Locale.ROOT));
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_success));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
            } else if (Constants.STATUS_LOCATION_UNAVAILABLE.equalsIgnoreCase(status)) {
                tvStatusBadge.setText("NO GPS FIX");
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            } else {
                tvStatusBadge.setText(status.toUpperCase(Locale.ROOT));
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
            }

            // Body
            tvMessageBody.setText(message.getDescription());

            // Location
            layoutLocation.setVisibility(View.VISIBLE);
            if (message.hasCoordinates()) {
                ivLocationIcon.setImageResource(R.drawable.ic_location_pin);
                ivLocationIcon.setColorFilter(ContextCompat.getColor(context, R.color.teal_accent));

                String accStr = message.getLocationAccuracy() != null
                        ? String.format(Locale.getDefault(), " (±%.1fm)", message.getLocationAccuracy())
                        : "";
                String coordsText = String.format(Locale.getDefault(), "Lat: %.5f° N, Lon: %.5f° E%s",
                        message.getLatitude(), message.getLongitude(), accStr);
                tvMessageLocation.setText(coordsText);
                tvMessageLocation.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            } else {
                ivLocationIcon.setImageResource(R.drawable.ic_location_disabled);
                ivLocationIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_warning));
                tvMessageLocation.setText(R.string.sos_coords_none);
                tvMessageLocation.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
            }

            // Timestamp
            tvMessageTime.setText(DateTimeUtils.getRelativeTimeSpan(message.getTimestamp()));

            // Sender
            String callsign = message.getSenderCallsign() != null ? message.getSenderCallsign() : "UNKNOWN";
            tvSenderCallsign.setText(String.format("Node: %s", callsign));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMessageClick(message);
                }
            });
        }
    }
}
