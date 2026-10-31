package com.blackout.emergency.warnings.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.blackout.emergency.database.BlackoutDatabase;
import com.blackout.emergency.database.PublicWarningDao;
import com.blackout.emergency.database.PublicWarningEntity;
import com.blackout.emergency.database.TrustedSigningKeyDao;
import com.blackout.emergency.database.TrustedSigningKeyEntity;
import com.blackout.emergency.database.WarningRelayHistoryDao;
import com.blackout.emergency.database.WarningRelayHistoryEntity;
import com.blackout.emergency.database.WarningRevisionDao;
import com.blackout.emergency.database.WarningRevisionEntity;
import com.blackout.emergency.database.WarningRevocationDao;
import com.blackout.emergency.database.WarningRevocationEntity;
import com.blackout.emergency.database.WarningSyncStateDao;
import com.blackout.emergency.database.WarningSyncStateEntity;
import com.blackout.emergency.warnings.crypto.TrustedKeyRepository;
import com.blackout.emergency.warnings.crypto.WarningCanonicalSerializer;
import com.blackout.emergency.warnings.crypto.WarningSignatureVerifier;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe central repository for public emergency warnings, revision histories,
 * cryptographic verification, and key persistence (Phase 7).
 */
public class WarningRepository {

    public enum WarningProcessResult {
        ACCEPTED_NEW,
        ACCEPTED_REVISION_UPDATE,
        DUPLICATE_IGNORED,
        REJECTED_LOWER_REVISION,
        REJECTED_VERIFICATION_FAILED,
        REJECTED_EXPIRED,
        REJECTED_INVALID_PAYLOAD,
        REVOCATION_RECORDED
    }

    public interface WarningProcessCallback {
        void onResult(@NonNull WarningProcessResult result, @Nullable PublicWarning warning, @NonNull String message);
    }

    public interface WarningListCallback {
        void onLoaded(@NonNull List<PublicWarning> warnings);
    }

    private static volatile WarningRepository instance;
    private final BlackoutDatabase db;
    private final PublicWarningDao warningDao;
    private final WarningRevisionDao revisionDao;
    private final TrustedSigningKeyDao keyDao;
    private final WarningRelayHistoryDao relayHistoryDao;
    private final WarningSyncStateDao syncStateDao;
    private final WarningRevocationDao revocationDao;

    private final TrustedKeyRepository keyRepository;
    private final WarningSignatureVerifier verifier;
    private final ExecutorService executor;

    public WarningRepository(@NonNull Context context) {
        this.db = BlackoutDatabase.getInstance(context);
        this.warningDao = db.publicWarningDao();
        this.revisionDao = db.warningRevisionDao();
        this.keyDao = db.trustedSigningKeyDao();
        this.relayHistoryDao = db.warningRelayHistoryDao();
        this.syncStateDao = db.warningSyncStateDao();
        this.revocationDao = db.warningRevocationDao();

        this.keyRepository = TrustedKeyRepository.getInstance();
        this.verifier = new WarningSignatureVerifier(keyRepository);
        this.executor = Executors.newSingleThreadExecutor();

        loadPersistedKeysAsync();
    }

    @VisibleForTesting
    public WarningRepository(@NonNull BlackoutDatabase db,
                             @NonNull TrustedKeyRepository keyRepository,
                             @NonNull ExecutorService executor) {
        this.db = db;
        this.warningDao = db.publicWarningDao();
        this.revisionDao = db.warningRevisionDao();
        this.keyDao = db.trustedSigningKeyDao();
        this.relayHistoryDao = db.warningRelayHistoryDao();
        this.syncStateDao = db.warningSyncStateDao();
        this.revocationDao = db.warningRevocationDao();

        this.keyRepository = keyRepository;
        this.verifier = new WarningSignatureVerifier(keyRepository);
        this.executor = executor;
    }

    public static synchronized WarningRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new WarningRepository(context.getApplicationContext());
        }
        return instance;
    }

    public TrustedKeyRepository getKeyRepository() {
        return keyRepository;
    }

    public WarningSignatureVerifier getVerifier() {
        return verifier;
    }

    // =========================================================================
    // Key Management
    // =========================================================================

    private void loadPersistedKeysAsync() {
        executor.execute(() -> {
            try {
                List<TrustedSigningKeyEntity> persisted = keyDao.getAllKeys();
                List<TrustedSigningKey> models = new ArrayList<>();
                for (TrustedSigningKeyEntity e : persisted) {
                    models.add(e.toModel());
                }
                keyRepository.registerKeys(models);
            } catch (Exception ignored) {}
        });
    }

    public void persistKey(@NonNull TrustedSigningKey key) {
        keyRepository.registerKey(key);
        executor.execute(() -> {
            keyDao.insertOrUpdate(TrustedSigningKeyEntity.fromModel(key));
        });
    }

    public void persistKeys(@NonNull List<TrustedSigningKey> keys) {
        keyRepository.registerKeys(keys);
        executor.execute(() -> {
            List<TrustedSigningKeyEntity> entities = new ArrayList<>();
            for (TrustedSigningKey k : keys) {
                entities.add(TrustedSigningKeyEntity.fromModel(k));
            }
            keyDao.insertAll(entities);
        });
    }

    // =========================================================================
    // Core Warning Ingestion & Revision Lifecycle
    // =========================================================================

    /**
     * Ingests, validates, cryptographically verifies, and persists a public emergency warning.
     * Enforces revision rollback protection, duplicate suppression, and revocation rules.
     */
    public void processAndStoreWarning(@NonNull final PublicWarning warning,
                                       @Nullable final WarningProcessCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Schema & Cryptographic Signature Verification
                WarningSignatureVerifier.VerificationResult vResult = verifier.verify(warning);
                warning.setVerificationStatus(vResult.getStatus());
                if (vResult.getCanonicalPayload() != null) {
                    warning.setCanonicalPayload(vResult.getCanonicalPayload());
                    warning.setPayloadHash(WarningCanonicalSerializer.computePayloadHash(vResult.getCanonicalPayload()));
                }

                if (!vResult.isAuthentic()) {
                    if (callback != null) {
                        callback.onResult(
                                WarningProcessResult.REJECTED_VERIFICATION_FAILED,
                                warning,
                                "Verification rejected: " + vResult.getMessage()
                        );
                    }
                    return;
                }

                // 2. Check Existing Database Record for Warning ID
                PublicWarningEntity existing = warningDao.getWarningById(warning.getWarningId());

                if (existing != null) {
                    if (existing.getRevision() > warning.getRevision()) {
                        // Rollback protection: Reject lower revision
                        if (callback != null) {
                            callback.onResult(
                                    WarningProcessResult.REJECTED_LOWER_REVISION,
                                    warning,
                                    "Rejected revision " + warning.getRevision() + " because newer revision "
                                            + existing.getRevision() + " already exists"
                            );
                        }
                        return;
                    }

                    if (existing.getRevision() == warning.getRevision()) {
                        // Duplicate revision already stored
                        if (callback != null) {
                            callback.onResult(
                                    WarningProcessResult.DUPLICATE_IGNORED,
                                    warning,
                                    "Duplicate warning revision " + warning.getRevision() + " already stored"
                            );
                        }
                        return;
                    }

                    // Higher revision: Supersedes existing warning.
                    // Archive old existing warning revision into warning_revisions table
                    WarningRevisionEntity archiveOld = new WarningRevisionEntity(
                            existing.getWarningId(),
                            existing.getRevision(),
                            existing.getTitle(),
                            existing.getDescription(),
                            existing.getCategory(),
                            existing.getSeverity(),
                            existing.getStatus(),
                            existing.getIssuingAuthority(),
                            existing.getSigningKeyId(),
                            existing.getSignatureAlgorithm(),
                            existing.getSignature(),
                            existing.getCanonicalPayload(),
                            existing.getAffectedRegionsJson(),
                            existing.getLatitude(),
                            existing.getLongitude(),
                            existing.getRadiusMeters(),
                            existing.getLanguage(),
                            existing.getIssuedAt(),
                            existing.getEffectiveAt(),
                            existing.getExpiresAt(),
                            existing.getLastReceivedAt()
                    );
                    revisionDao.insertRevision(archiveOld);

                    // Update main table to new revision
                    PublicWarningEntity newEntity = PublicWarningEntity.fromModel(warning);
                    warningDao.insertOrUpdate(newEntity);

                    // Check if new revision is a revocation
                    if (PublicWarning.STATUS_REVOKED.equalsIgnoreCase(warning.getStatus())
                            || PublicWarning.VERIFY_REVOKED_WARNING.equals(warning.getVerificationStatus())) {
                        revocationDao.insertOrUpdate(new WarningRevocationEntity(
                                warning.getWarningId(),
                                warning.getRevision(),
                                "Formal Revocation Revision " + warning.getRevision(),
                                warning.getSignature(),
                                System.currentTimeMillis()
                        ));
                    }

                    if (callback != null) {
                        callback.onResult(
                                WarningProcessResult.ACCEPTED_REVISION_UPDATE,
                                warning,
                                "Successfully updated warning to revision " + warning.getRevision()
                        );
                    }

                } else {
                    // First time receiving this warning
                    PublicWarningEntity entity = PublicWarningEntity.fromModel(warning);
                    warningDao.insertOrUpdate(entity);

                    // Record initial revision in history
                    WarningRevisionEntity initialRev = new WarningRevisionEntity(
                            warning.getWarningId(),
                            warning.getRevision(),
                            warning.getTitle(),
                            warning.getDescription(),
                            warning.getCategory(),
                            warning.getSeverity(),
                            warning.getStatus(),
                            warning.getIssuingAuthority(),
                            warning.getSigningKeyId(),
                            warning.getSignatureAlgorithm(),
                            warning.getSignature(),
                            warning.getCanonicalPayload(),
                            entity.getAffectedRegionsJson(),
                            warning.getLatitude(),
                            warning.getLongitude(),
                            warning.getRadiusMeters(),
                            warning.getLanguage(),
                            warning.getIssuedAt(),
                            warning.getEffectiveAt(),
                            warning.getExpiresAt(),
                            System.currentTimeMillis()
                    );
                    revisionDao.insertRevision(initialRev);

                    if (callback != null) {
                        callback.onResult(
                                WarningProcessResult.ACCEPTED_NEW,
                                warning,
                                "Successfully verified and stored new official warning"
                        );
                    }
                }

            } catch (Exception e) {
                if (callback != null) {
                    callback.onResult(
                            WarningProcessResult.REJECTED_INVALID_PAYLOAD,
                            warning,
                            "Database insertion error: " + e.getMessage()
                    );
                }
            }
        });
    }

    // =========================================================================
    // Querying Methods
    // =========================================================================

    public void getActiveVerifiedWarnings(@NonNull final WarningListCallback callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                List<PublicWarningEntity> entities = warningDao.getActiveVerifiedWarnings(now);
                List<PublicWarning> models = new ArrayList<>();
                for (PublicWarningEntity e : entities) {
                    models.add(e.toModel());
                }
                callback.onLoaded(models);
            } catch (Exception e) {
                callback.onLoaded(Collections.emptyList());
            }
        });
    }

    public void getAllWarnings(@NonNull final WarningListCallback callback) {
        executor.execute(() -> {
            try {
                List<PublicWarningEntity> entities = warningDao.getAllWarnings();
                List<PublicWarning> models = new ArrayList<>();
                for (PublicWarningEntity e : entities) {
                    models.add(e.toModel());
                }
                callback.onLoaded(models);
            } catch (Exception e) {
                callback.onLoaded(Collections.emptyList());
            }
        });
    }

    public void getEligibleRelayInventory(final InventoryCallback callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                List<PublicWarningDao.InventoryItem> items = warningDao.getEligibleWarningInventory(now);
                callback.onLoaded(items != null ? items : Collections.emptyList());
            } catch (Exception e) {
                callback.onLoaded(Collections.emptyList());
            }
        });
    }

    public interface InventoryCallback {
        void onLoaded(@NonNull List<PublicWarningDao.InventoryItem> items);
    }

    public void getWarningById(@NonNull final String warningId, final SingleWarningCallback callback) {
        executor.execute(() -> {
            try {
                PublicWarningEntity entity = warningDao.getWarningById(warningId);
                callback.onLoaded(entity != null ? entity.toModel() : null);
            } catch (Exception e) {
                callback.onLoaded(null);
            }
        });
    }

    public interface SingleWarningCallback {
        void onLoaded(@Nullable PublicWarning warning);
    }

    public void recordRelayEvent(@NonNull String warningId, int revision,
                                 @NonNull String peerDeviceId, @NonNull String action,
                                 @Nullable String details) {
        executor.execute(() -> {
            try {
                warningDao.updateRelayedTimestamp(warningId, System.currentTimeMillis());
                relayHistoryDao.insert(new WarningRelayHistoryEntity(
                        warningId, revision, peerDeviceId, action, System.currentTimeMillis(), details
                ));
            } catch (Exception ignored) {}
        });
    }

    public void updateSyncState(@NonNull String syncKey, int count, @NonNull String status, @Nullable String error) {
        executor.execute(() -> {
            try {
                syncStateDao.setSyncState(new WarningSyncStateEntity(
                        syncKey, System.currentTimeMillis(), count, status, error
                ));
            } catch (Exception ignored) {}
        });
    }
}
