import { describe, it, expect } from 'vitest';
import { buildCanonicalString, verifySignatureInBrowser, ROOT_PUBLIC_KEY_BASE64 } from '../services/signingService';

describe('Phase 7 Cryptographic Signing & Canonical Serialization Service', () => {
  const sampleWarning = {
    warningId: 'd3b07384-d113-46fb-9b24-9b2bfa6eb542',
    protocolVersion: 1,
    revision: 1,
    status: 'PUBLISHED',
    category: 'FLOOD',
    severity: 'CRITICAL',
    issuingAuthority: 'BLACKOUT Civil Defense & Emergency Response Administration',
    title: 'Severe Flash Flooding Alert',
    description: 'Immediate flash flooding reported in low-lying areas. Move to higher ground immediately.',
    issuedAt: 1793400000000,
    effectiveAt: 1793400000000,
    expiresAt: 1850000000000,
    language: 'en',
    signingKeyId: 'KEY-BLACKOUT-ROOT-2026',
    signatureAlgorithm: 'Ed25519',
    latitude: 13.082680,
    longitude: 80.270718,
    radiusMeters: 5000.0,
    affectedRegions: ['Sector 4', 'Central Basin', 'North Riverbank'],
  };

  const VALID_SIGNATURE = 'sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==';

  it('builds canonical string with sorted regions and formatted floats', () => {
    const canonical = buildCanonicalString(sampleWarning);
    expect(canonical).toContain('BLACKOUT_WARNING_V1');
    expect(canonical).toContain('warningId=d3b07384-d113-46fb-9b24-9b2bfa6eb542');
    expect(canonical).toContain('category=FLOOD');
    expect(canonical).toContain('severity=CRITICAL');
    expect(canonical).toContain('affectedRegions=Central Basin,North Riverbank,Sector 4');
    expect(canonical).toContain('latitude=13.082680');
    expect(canonical).toContain('longitude=80.270718');
    expect(canonical).toContain('radiusMeters=5000.00');
  });

  it('verifies genuine Ed25519 signature in browser WebCrypto', async () => {
    const canonical = buildCanonicalString(sampleWarning);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(true);
  });

  it('detects tampering of warning title', async () => {
    const altered = { ...sampleWarning, title: 'Tampered Flood Warning' };
    const canonical = buildCanonicalString(altered);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(false);
  });

  it('detects tampering of warning description', async () => {
    const altered = { ...sampleWarning, description: 'Fake safety all-clear.' };
    const canonical = buildCanonicalString(altered);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(false);
  });

  it('detects tampering of severity', async () => {
    const altered = { ...sampleWarning, severity: 'ADVISORY' };
    const canonical = buildCanonicalString(altered);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(false);
  });

  it('detects tampering of revision number', async () => {
    const altered = { ...sampleWarning, revision: 2 };
    const canonical = buildCanonicalString(altered);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(false);
  });

  it('detects tampering of geographic coordinates', async () => {
    const altered = { ...sampleWarning, latitude: 13.999999 };
    const canonical = buildCanonicalString(altered);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, VALID_SIGNATURE);
    expect(valid).toBe(false);
  });

  it('rejects malformed signature gracefully', async () => {
    const canonical = buildCanonicalString(sampleWarning);
    const valid = await verifySignatureInBrowser(ROOT_PUBLIC_KEY_BASE64, canonical, 'INVALID_BASE64_SIG');
    expect(valid).toBe(false);
  });

  it('rejects unauthorized roles from signing warnings (Phase 8 RBAC enforcement)', async () => {
    const { signingService } = await import('../services/signingService');
    const unauthorizedActor = {
      id: 'usr-viewer-1',
      email: 'viewer@blackout.test',
      role: 'VIEWER',
    };
    const res = await signingService.signAndPublishWarning('any-id', unauthorizedActor);
    expect(res.error).toContain('FORBIDDEN');
    expect(res.data).toBeNull();
  });

  it('rejects unauthorized roles from revoking warnings (Phase 8 RBAC enforcement)', async () => {
    const { signingService } = await import('../services/signingService');
    const unauthorizedActor = {
      id: 'usr-coord-1',
      email: 'coordinator@blackout.test',
      role: 'FIELD_RESPONSE_COORDINATOR',
    };
    const res = await signingService.revokeWarning('any-id', 'test cancellation', unauthorizedActor);
    expect(res.error).toContain('FORBIDDEN');
    expect(res.data).toBeNull();
  });
});

