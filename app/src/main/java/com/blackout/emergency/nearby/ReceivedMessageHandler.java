package com.blackout.emergency.nearby;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.models.EmergencyMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles incoming raw byte payloads from Nearby Connections.
 * Validates payload, checks against duplicate records in Room database,
 * and persists valid incoming SOS alerts.
 */
public class ReceivedMessageHandler {

    public enum ProcessingOutcome {
        SUCCESS,
        DUPLICATE_IGNORED,
        INVALID_PAYLOAD,
        DATABASE_ERROR
    }

    public interface MessageProcessingCallback {
        void onProcessed(@NonNull ProcessingOutcome outcome, @Nullable EmergencyMessage message, @Nullable String errorMessage);
    }

    private final EmergencyRepository repository;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                return new Handler(looper);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public ReceivedMessageHandler(Context context) {
        this.repository = EmergencyRepository.getInstance(context.getApplicationContext());
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = createSafeMainHandler();
    }

    public ReceivedMessageHandler(EmergencyRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor();
        this.mainHandler = createSafeMainHandler();
    }

    /**
     * Processes an incoming raw byte payload asynchronously.
     */
    public void processIncomingPayload(@Nullable final byte[] payloadBytes, @NonNull final MessageProcessingCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Deserialize and validate payload
                final EmergencyMessage message = SosPayloadSerializer.deserialize(payloadBytes);

                // 2. Insert into repository with duplicate protection
                repository.insertReceivedMessage(message, new EmergencyRepository.Callback<Boolean>() {
                    @Override
                    public void onComplete(Boolean success) {
                        if (Boolean.TRUE.equals(success)) {
                            postOutcome(callback, ProcessingOutcome.SUCCESS, message, null);
                        } else {
                            // If insertReceivedMessage returns false, it already exists in Room
                            postOutcome(callback, ProcessingOutcome.DUPLICATE_IGNORED, message, "Duplicate SOS message already stored.");
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        postOutcome(callback, ProcessingOutcome.DATABASE_ERROR, message, e.getMessage());
                    }
                });

            } catch (SosPayloadSerializer.PayloadValidationException pve) {
                postOutcome(callback, ProcessingOutcome.INVALID_PAYLOAD, null, pve.getMessage());
            } catch (Exception e) {
                postOutcome(callback, ProcessingOutcome.DATABASE_ERROR, null, e.getMessage());
            }
        });
    }

    private void postOutcome(@NonNull MessageProcessingCallback callback,
                            @NonNull ProcessingOutcome outcome,
                            @Nullable EmergencyMessage message,
                            @Nullable String error) {
        if (mainHandler != null) {
            mainHandler.post(() -> callback.onProcessed(outcome, message, error));
        } else {
            callback.onProcessed(outcome, message, error);
        }
    }
}
