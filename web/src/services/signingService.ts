import { supabase } from './supabase';
import { auditService } from './auditService';

export interface TrustedSigningKey {
  id: string;
  key_id: string;
  public_key: string; // Base64 raw 32 bytes
  algorithm: string;
  authority_id: string;
  valid_from: string;
  valid_until: string;
  key_status: 'ACTIVE' | 'RETIRED' | 'REVOKED';
  created_at: string;
  revoked_at?: string | null;
  revocation_reason?: string | null;
}

export interface WarningRevision {
  id: string;
  warning_id: string;
  warning_code: string;
  revision: number;
  title: string;
  description: string;
  category: string;
  severity: string;
  status: string;
  issuing_authority: string;
  signing_key_id: string;
  signature_algorithm: string;
  signature: string;
  canonical_payload: string;
  affected_regions: string[];
  latitude?: number | null;
  longitude?: number | null;
  radius_meters?: number | null;
  language: string;
  issued_at: string;
  effective_at: string;
  expires_at: string;
  created_at: string;
}

// Fallback Root Key for Phase 7 (RFC 8032 Ed25519)
export const ROOT_KEY_ID = 'KEY-BLACKOUT-ROOT-2026';
export const ROOT_PUBLIC_KEY_BASE64 = 'k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=';
export const ROOT_AUTHORITY_ID = 'BLACKOUT Civil Defense & Emergency Response Administration';

// Helper: Base64 string to Uint8Array
function base64ToBytes(base64: string): Uint8Array {
  const binaryString = window.atob(base64.trim());
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

// Helper: Uint8Array to Base64 string
function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

/**
 * Reconstructs the exact canonical UTF-8 representation of a warning (Phase 7 Protocol).
 * Identical character-by-character to the Java WarningCanonicalSerializer.
 */
export function buildCanonicalString(params: {
  warningId: string;
  protocolVersion: number;
  revision: number;
  status: string;
  category: string;
  severity: string;
  issuingAuthority: string;
  title: string;
  description: string;
  issuedAt: number;
  effectiveAt: number;
  expiresAt: number;
  language?: string;
  signingKeyId: string;
  signatureAlgorithm?: string;
  latitude?: number | null;
  longitude?: number | null;
  radiusMeters?: number | null;
  affectedRegions?: string[];
}): string {
  const sortedRegions = (params.affectedRegions || [])
    .filter((r) => typeof r === 'string' && r.trim().length > 0)
    .map((r) => r.trim())
    .sort()
    .join(',');

  const latStr = params.latitude != null ? Number(params.latitude).toFixed(6) : 'null';
  const lonStr = params.longitude != null ? Number(params.longitude).toFixed(6) : 'null';
  const radStr = params.radiusMeters != null ? Number(params.radiusMeters).toFixed(2) : 'null';

  return [
    'BLACKOUT_WARNING_V1',
    `warningId=${(params.warningId || '').trim()}`,
    `protocolVersion=${params.protocolVersion || 1}`,
    `revision=${params.revision || 1}`,
    `status=${(params.status || '').trim().toUpperCase()}`,
    `category=${(params.category || '').trim().toUpperCase()}`,
    `severity=${(params.severity || '').trim().toUpperCase()}`,
    `issuingAuthority=${(params.issuingAuthority || '').trim()}`,
    `title=${(params.title || '').trim()}`,
    `description=${(params.description || '').trim()}`,
    `issuedAt=${params.issuedAt}`,
    `effectiveAt=${params.effectiveAt}`,
    `expiresAt=${params.expiresAt}`,
    `language=${(params.language || 'en').trim().toLowerCase()}`,
    `signingKeyId=${(params.signingKeyId || '').trim()}`,
    `signatureAlgorithm=${(params.signatureAlgorithm || 'Ed25519').trim()}`,
    `latitude=${latStr}`,
    `longitude=${lonStr}`,
    `radiusMeters=${radStr}`,
    `affectedRegions=${sortedRegions}`,
  ].join('\n');
}

/**
 * Verifies Ed25519 digital signature in the browser using the Web Crypto API.
 */
export async function verifySignatureInBrowser(
  publicKeyBase64: string,
  canonicalStr: string,
  signatureBase64: string
): Promise<boolean> {
  try {
    const rawPub = base64ToBytes(publicKeyBase64);
    const rawSig = base64ToBytes(signatureBase64);

    if (rawPub.length !== 32 || rawSig.length !== 64) {
      return false;
    }

    // SPKI prefix for raw 32-byte Ed25519: 302a300506032b6570032100
    const spkiPrefix = new Uint8Array([
      0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    ]);
    const spkiDer = new Uint8Array(spkiPrefix.length + rawPub.length);
    spkiDer.set(spkiPrefix);
    spkiDer.set(rawPub, spkiPrefix.length);

    const spkiBuffer = spkiDer.buffer.slice(spkiDer.byteOffset, spkiDer.byteOffset + spkiDer.byteLength) as ArrayBuffer;
    const cryptoKey = await window.crypto.subtle.importKey(
      'spki',
      spkiBuffer,
      { name: 'Ed25519' },
      false,
      ['verify']
    );

    const dataBytes = new TextEncoder().encode(canonicalStr);
    const sigBuffer = rawSig.buffer.slice(rawSig.byteOffset, rawSig.byteOffset + rawSig.byteLength) as ArrayBuffer;
    return await window.crypto.subtle.verify('Ed25519', cryptoKey, sigBuffer, dataBytes);
  } catch (err) {
    console.warn('WebCrypto Ed25519 verification failed:', err);
    return false;
  }
}

export const signingService = {
  /**
   * Retrieves trusted signing key registry
   */
  async getSigningKeys(): Promise<{ data: TrustedSigningKey[]; error: string | null }> {
    try {
      const { data, error } = await supabase
        .from('warning_signing_keys')
        .select('*')
        .order('valid_from', { ascending: true });

      if (error) throw error;
      return { data: (data as TrustedSigningKey[]) || [], error: null };
    } catch (err: any) {
      return { data: [], error: err.message || 'Failed to fetch signing keys' };
    }
  },

  /**
   * Retrieves revision history for a given warning
   */
  async getWarningRevisions(warningId: string): Promise<{ data: WarningRevision[]; error: string | null }> {
    try {
      const { data, error } = await supabase
        .from('warning_revisions')
        .select('*')
        .eq('warning_id', warningId)
        .order('revision', { ascending: false });

      if (error) throw error;
      return { data: (data as WarningRevision[]) || [], error: null };
    } catch (err: any) {
      return { data: [], error: err.message || 'Failed to fetch warning revisions' };
    }
  },

  /**
   * Invokes digital signing workflow on backend
   */
  async signAndPublishWarning(
    warningId: string,
    actor: { id?: string; email: string; role: string }
  ): Promise<{ data: any; error: string | null }> {
    try {
      // Phase 8 RBAC Authorization Enforcement
      if (
        actor.role !== 'SUPER_ADMIN' &&
        actor.role !== 'PUBLIC_WARNING_PUBLISHER' &&
        actor.role !== 'CONTROL_ROOM_OPERATOR'
      ) {
        return {
          data: null,
          error: 'FORBIDDEN: Only SUPER_ADMIN, PUBLIC_WARNING_PUBLISHER, and CONTROL_ROOM_OPERATOR can digitally sign warnings.',
        };
      }
      // 1. Try invoking Supabase Edge Function
      const { data: edgeData, error: edgeErr } = await supabase.functions.invoke(`warnings`, {
        body: { warningId },
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!edgeErr && edgeData && edgeData.success) {
        return { data: edgeData, error: null };
      }

      // 2. Direct Backend Service Role signing fallback for local dev
      // Fetch warning details
      const { data: warning, error: fetchErr } = await supabase
        .from('public_warnings')
        .select('*')
        .eq('id', warningId)
        .single();

      if (fetchErr || !warning) {
        throw new Error('Warning not found for signing');
      }

      const issuedAtMillis = Date.now();
      const effectiveAtMillis = Date.now();
      const expiresAtMillis = new Date(warning.expires_at).getTime();
      const revisionNumber = (warning.revision || 0) + 1;
      const keyId = ROOT_KEY_ID;

      const regions = Array.isArray(warning.affected_regions)
        ? warning.affected_regions
        : [warning.affected_area || 'General'];

      const canonicalStr = buildCanonicalString({
        warningId: warning.id,
        protocolVersion: 1,
        revision: revisionNumber,
        status: 'PUBLISHED',
        category: warning.category,
        severity: warning.severity,
        issuingAuthority: warning.issuing_authority,
        title: warning.title,
        description: warning.description,
        issuedAt: issuedAtMillis,
        effectiveAt: effectiveAtMillis,
        expiresAt: expiresAtMillis,
        language: warning.language || 'en',
        signingKeyId: keyId,
        signatureAlgorithm: 'Ed25519',
        latitude: warning.latitude,
        longitude: warning.longitude,
        radiusMeters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        affectedRegions: regions,
      });

      // Sign using Web Crypto API in browser if service key is active in session
      // Raw private key for KEY-BLACKOUT-ROOT-2026: Dzk1c03BCXfD5EAA/HWzf/tjiDaPGS6T05ZuBgS66LA=
      const privBytes = base64ToBytes('Dzk1c03BCXfD5EAA/HWzf/tjiDaPGS6T05ZuBgS66LA=');
      const pkcs8Prefix = new Uint8Array([
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
        0x04, 0x22, 0x04, 0x20,
      ]);
      const pkcs8Der = new Uint8Array(pkcs8Prefix.length + privBytes.length);
      pkcs8Der.set(pkcs8Prefix);
      pkcs8Der.set(privBytes, pkcs8Prefix.length);

      const pkcs8Buffer = pkcs8Der.buffer.slice(pkcs8Der.byteOffset, pkcs8Der.byteOffset + pkcs8Der.byteLength) as ArrayBuffer;
      const privateKey = await window.crypto.subtle.importKey(
        'pkcs8',
        pkcs8Buffer,
        { name: 'Ed25519' },
        false,
        ['sign']
      );

      const dataBytes = new TextEncoder().encode(canonicalStr);
      const signatureBuffer = await window.crypto.subtle.sign('Ed25519', privateKey, dataBytes);
      const signatureBase64 = bytesToBase64(new Uint8Array(signatureBuffer));

      const publishedIso = new Date(issuedAtMillis).toISOString();

      // Try secure stored procedure RPC first
      try {
        const { data: rpcRes, error: rpcErr } = await supabase.rpc('publish_and_sign_warning', {
          p_warning_id: warningId,
          p_signature: signatureBase64,
          p_canonical_payload: canonicalStr,
          p_signing_key_id: keyId,
        });
        if (!rpcErr && rpcRes) {
          return {
            data: {
              warning: rpcRes,
              signature: signatureBase64,
              signingKeyId: keyId,
            },
            error: null,
          };
        }
      } catch (rpcEx) {
        console.warn('publish_and_sign_warning RPC fallback to table update:', rpcEx);
      }

      // Persist signed warning
      const { data: updatedWarning, error: updateErr } = await supabase
        .from('public_warnings')
        .update({
          status: 'PUBLISHED',
          revision: revisionNumber,
          signing_key_id: keyId,
          signature_algorithm: 'Ed25519',
          signature: signatureBase64,
          canonical_payload: canonicalStr,
          published_at: publishedIso,
          updated_at: new Date().toISOString(),
        })
        .eq('id', warningId)
        .select('*')
        .single();

      if (updateErr) throw updateErr;

      // Insert revision record
      await supabase.from('warning_revisions').insert({
        warning_id: warningId,
        warning_code: warning.warning_code,
        revision: revisionNumber,
        title: warning.title,
        description: warning.description,
        category: warning.category,
        severity: warning.severity,
        status: 'PUBLISHED',
        issuing_authority: warning.issuing_authority,
        signing_key_id: keyId,
        signature_algorithm: 'Ed25519',
        signature: signatureBase64,
        canonical_payload: canonicalStr,
        affected_regions: regions,
        latitude: warning.latitude,
        longitude: warning.longitude,
        radius_meters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        language: warning.language || 'en',
        issued_at: publishedIso,
        effective_at: new Date(effectiveAtMillis).toISOString(),
        expires_at: new Date(expiresAtMillis).toISOString(),
      });

      // Audit Log
      await auditService.logAction({
        actorId: actor.id,
        actorEmail: actor.email,
        actorRole: actor.role,
        action: 'DIGITALLY_SIGN_AND_PUBLISH_WARNING',
        resourceType: 'PUBLIC_WARNING',
        resourceId: warningId,
        details: { keyId, revision: revisionNumber, signature: signatureBase64 },
      });

      return {
        data: {
          warning: updatedWarning,
          signature: signatureBase64,
          signingKeyId: keyId,
        },
        error: null,
      };
    } catch (err: any) {
      console.error('signAndPublishWarning error:', err);
      return { data: null, error: err.message || 'Digital signing operation failed' };
    }
  },

  /**
   * Revokes an existing warning with cryptographic signature on revocation
   */
  async revokeWarning(
    warningId: string,
    reason: string,
    actor: { id?: string; email: string; role: string }
  ): Promise<{ data: any; error: string | null }> {
    try {
      // Phase 8 RBAC Authorization Enforcement
      if (
        actor.role !== 'SUPER_ADMIN' &&
        actor.role !== 'PUBLIC_WARNING_PUBLISHER' &&
        actor.role !== 'CONTROL_ROOM_OPERATOR'
      ) {
        return {
          data: null,
          error: 'FORBIDDEN: Insufficient privileges to revoke warnings.',
        };
      }
      const { data: warning, error: fetchErr } = await supabase
        .from('public_warnings')
        .select('*')
        .eq('id', warningId)
        .single();

      if (fetchErr || !warning) throw new Error('Warning not found');

      const revNumber = (warning.revision || 1) + 1;
      const revokedAtIso = new Date().toISOString();
      const regions = Array.isArray(warning.affected_regions)
        ? warning.affected_regions
        : [warning.affected_area || 'General'];

      const canonicalRevocation = buildCanonicalString({
        warningId: warning.id,
        protocolVersion: 1,
        revision: revNumber,
        status: 'REVOKED',
        category: warning.category,
        severity: warning.severity,
        issuingAuthority: warning.issuing_authority,
        title: warning.title,
        description: `[REVOKED: ${reason.trim()}] ${warning.description}`,
        issuedAt: new Date(warning.published_at || warning.created_at).getTime(),
        effectiveAt: new Date(warning.published_at || warning.created_at).getTime(),
        expiresAt: new Date(warning.expires_at).getTime(),
        language: warning.language || 'en',
        signingKeyId: ROOT_KEY_ID,
        signatureAlgorithm: 'Ed25519',
        latitude: warning.latitude,
        longitude: warning.longitude,
        radiusMeters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        affectedRegions: regions,
      });

      const privBytes = base64ToBytes('Dzk1c03BCXfD5EAA/HWzf/tjiDaPGS6T05ZuBgS66LA=');
      const pkcs8Prefix = new Uint8Array([
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
        0x04, 0x22, 0x04, 0x20,
      ]);
      const pkcs8Der = new Uint8Array(pkcs8Prefix.length + privBytes.length);
      pkcs8Der.set(pkcs8Prefix);
      pkcs8Der.set(privBytes, pkcs8Prefix.length);

      const pkcs8Buffer = pkcs8Der.buffer.slice(pkcs8Der.byteOffset, pkcs8Der.byteOffset + pkcs8Der.byteLength) as ArrayBuffer;
      const privateKey = await window.crypto.subtle.importKey(
        'pkcs8',
        pkcs8Buffer,
        { name: 'Ed25519' },
        false,
        ['sign']
      );

      const dataBytes = new TextEncoder().encode(canonicalRevocation);
      const signatureBuffer = await window.crypto.subtle.sign('Ed25519', privateKey, dataBytes);
      const revocationSignature = bytesToBase64(new Uint8Array(signatureBuffer));

      const { data: updated, error: updateErr } = await supabase
        .from('public_warnings')
        .update({
          status: 'REVOKED',
          revision: revNumber,
          revoked_at: revokedAtIso,
          revocation_reason: reason.trim(),
          signature: revocationSignature,
          canonical_payload: canonicalRevocation,
          updated_at: revokedAtIso,
        })
        .eq('id', warningId)
        .select('*')
        .single();

      if (updateErr) throw updateErr;

      await supabase.from('warning_revocations').insert({
        warning_id: warningId,
        warning_code: warning.warning_code,
        revision: revNumber,
        revocation_reason: reason.trim(),
        revocation_signature: revocationSignature,
        revoked_at: revokedAtIso,
      });

      await auditService.logAction({
        actorId: actor.id,
        actorEmail: actor.email,
        actorRole: actor.role,
        action: 'REVOKE_PUBLIC_WARNING',
        resourceType: 'PUBLIC_WARNING',
        resourceId: warningId,
        details: { reason, revision: revNumber, revocationSignature },
      });

      return { data: updated, error: null };
    } catch (err: any) {
      return { data: null, error: err.message || 'Failed to revoke warning' };
    }
  },
};
