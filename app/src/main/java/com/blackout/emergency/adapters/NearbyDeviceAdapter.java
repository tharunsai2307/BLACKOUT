package com.blackout.emergency.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.blackout.emergency.R;
import com.blackout.emergency.nearby.NearbyDevice;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying real discovered peer devices from Nearby Connections
 * and indicating autonomous mesh gateway capability (Phase 12).
 */
public class NearbyDeviceAdapter extends RecyclerView.Adapter<NearbyDeviceAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onConnectClick(@NonNull NearbyDevice device);
    }

    private final List<NearbyDevice> deviceList = new ArrayList<>();
    private OnDeviceClickListener listener;

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<NearbyDevice> newDevices) {
        this.deviceList.clear();
        if (newDevices != null) {
            this.deviceList.addAll(newDevices);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nearby_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        NearbyDevice device = deviceList.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvEndpoint;
        private final TextView tvStatusBadge;
        private final MaterialButton btnConnect;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvEndpoint = itemView.findViewById(R.id.tv_device_endpoint);
            tvStatusBadge = itemView.findViewById(R.id.tv_device_status_badge);
            btnConnect = itemView.findViewById(R.id.btn_device_connect);
        }

        public void bind(final NearbyDevice device, final OnDeviceClickListener clickListener) {
            Context context = itemView.getContext();

            tvName.setText(device.getEndpointName());
            if (device.isGateway()) {
                String net = device.getNetworkType() != null ? device.getNetworkType() : "Online";
                tvEndpoint.setText(String.format("Gateway Endpoint: %s • Net: %s", device.getEndpointId(), net));
            } else {
                tvEndpoint.setText(String.format("Endpoint ID: %s", device.getEndpointId()));
            }

            switch (device.getState()) {
                case CONNECTED:
                    if (device.isGateway()) {
                        String net = device.getNetworkType() != null ? device.getNetworkType() : "Online";
                        tvStatusBadge.setText("GATEWAY (" + net.toUpperCase() + ")");
                        tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
                    } else {
                        tvStatusBadge.setText("CONNECTED");
                        tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_success));
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
                    }
                    btnConnect.setVisibility(View.GONE);
                    break;
                case CONNECTING:
                    tvStatusBadge.setText("CONNECTING");
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                    btnConnect.setVisibility(View.VISIBLE);
                    btnConnect.setEnabled(false);
                    btnConnect.setText("Connecting...");
                    break;
                case REQUESTED_INCOMING:
                case REQUESTED_OUTGOING:
                    tvStatusBadge.setText("REQUESTED");
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning));
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                    btnConnect.setVisibility(View.VISIBLE);
                    btnConnect.setEnabled(false);
                    btnConnect.setText("Pending...");
                    break;
                case DISCONNECTED:
                    tvStatusBadge.setText("DISCONNECTED");
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_offline));
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                    btnConnect.setVisibility(View.VISIBLE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText("Reconnect");
                    break;
                case DISCOVERED:
                default:
                    tvStatusBadge.setText("DISCOVERED");
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.teal_accent));
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal);
                    btnConnect.setVisibility(View.VISIBLE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText("Connect");
                    break;
            }

            btnConnect.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onConnectClick(device);
                }
            });
        }
    }
}
