package com.blackout.emergency.gateway;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service monitoring device internet connectivity to coordinate the Internet Gateway.
 * Registers network callbacks with Android ConnectivityManager and triggers
 * automatic pending SOS alert uploads when internet connectivity is detected.
 */
public class InternetGatewayService {

    private static final String TAG = "InternetGatewayService";

    public interface ConnectivityListener {
        void onConnectivityChanged(boolean isConnected, @NonNull String networkType);
    }

    private static volatile InternetGatewayService instance;
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler;
    private final Set<ConnectivityListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private volatile String currentNetworkType = "Offline";
    private ConnectivityManager.NetworkCallback networkCallback;

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                return new Handler(looper);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void postToMain(Runnable r) {
        if (r == null) return;
        if (mainHandler != null) {
            mainHandler.post(r);
        } else {
            r.run();
        }
    }

    private InternetGatewayService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.mainHandler = createSafeMainHandler();
        initNetworkMonitoring();
    }

    public static synchronized InternetGatewayService getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new InternetGatewayService(context);
        }
        return instance;
    }

    @VisibleForTesting
    public InternetGatewayService(@Nullable Context context,
                                  @Nullable ConnectivityManager connectivityManager) {
        this.context = context;
        this.connectivityManager = connectivityManager;
        this.mainHandler = createSafeMainHandler();
    }

    public void registerListener(@NonNull ConnectivityListener listener) {
        listeners.add(listener);
        // Immediately notify current state
        listener.onConnectivityChanged(isConnected.get(), currentNetworkType);
    }

    public void unregisterListener(@NonNull ConnectivityListener listener) {
        listeners.remove(listener);
    }

    public boolean isInternetAvailable() {
        return isConnected.get();
    }

    @NonNull
    public String getCurrentNetworkType() {
        return currentNetworkType;
    }

    private void initNetworkMonitoring() {
        if (connectivityManager == null) {
            return;
        }

        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    checkAndNotifyNetworkState();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    checkAndNotifyNetworkState();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    checkAndNotifyNetworkState();
                }
            };

            connectivityManager.registerNetworkCallback(request, networkCallback);
            checkAndNotifyNetworkState();
        } catch (Exception ignored) {}
    }

    public void checkAndNotifyNetworkState() {
        boolean connected = false;
        String type = "Offline";

        if (connectivityManager != null) {
            try {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        connected = true;
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            type = "Wi-Fi";
                        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            type = "Cellular";
                        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            type = "Ethernet";
                        } else {
                            type = "Online";
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        boolean prevConnected = isConnected.getAndSet(connected);
        currentNetworkType = type;

        final boolean stateChanged = (prevConnected != connected);
        final boolean finalConnected = connected;
        final String finalType = type;

        postToMain(() -> {
            synchronized (listeners) {
                for (ConnectivityListener listener : listeners) {
                    if (listener != null) {
                        listener.onConnectivityChanged(finalConnected, finalType);
                    }
                }
            }
        });

        // If newly connected or verified connected, trigger upload of pending alerts
        if (connected && context != null) {
            try {
                PendingUploadManager.getInstance(context).flushPendingUploads(null);
                com.blackout.emergency.warnings.sync.WarningSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception ignored) {}
        }

        // Phase 12: Broadcast updated gateway capability across mesh peers when internet state changes
        if (stateChanged && context != null) {
            try {
                com.blackout.emergency.relay.RelayEngine.getInstance(context).broadcastGatewayCapability();
            } catch (Exception ignored) {}
        }
    }

    public void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }
}
