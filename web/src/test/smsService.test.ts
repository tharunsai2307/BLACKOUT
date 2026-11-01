import { describe, it, expect, vi, beforeEach } from 'vitest';
import { smsService } from '../services/smsService';
import { supabase } from '../services/supabase';

// Mock Supabase client
vi.mock('../services/supabase', () => ({
  supabase: {
    auth: {
      getSession: vi.fn(),
    },
    functions: {
      invoke: vi.fn(),
    },
    from: vi.fn(),
  },
}));

describe('SmsService Emergency Notifications & Recipient Consent', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('1. Rejects SMS dispatch if operator session is unauthorized / missing', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: null },
    });

    const res = await smsService.dispatchEmergencySms({
      scenario: 'EMERGENCY_CONTACT_ALERT',
      recipientPhone: '+919876543210',
      templateId: 'EMERGENCY_CONTACT_SOS',
      templateParameters: {},
    });

    expect(res.success).toBe(false);
    expect(res.error).toContain('Unauthorized');
  });

  it('2. Successfully dispatches SMS and returns provider accepted status', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: { access_token: 'mock-valid-jwt-token' } },
    });

    (supabase.functions.invoke as any).mockResolvedValue({
      data: {
        success: true,
        requestId: 'req-uuid-1234',
        status: 'PROVIDER_ACCEPTED',
        provider: 'MOCK_PROVIDER',
        messagePreview: 'BLACKOUT ALERT: SOS reported...',
      },
      error: null,
    });

    const res = await smsService.dispatchEmergencySms({
      scenario: 'EMERGENCY_CONTACT_ALERT',
      recipientPhone: '+919876543210',
      templateId: 'EMERGENCY_CONTACT_SOS',
      templateParameters: { name: 'Civilian', category: 'Medical Emergency' },
      incidentId: 'incident-123',
    });

    expect(res.success).toBe(true);
    expect(res.status).toBe('PROVIDER_ACCEPTED');
    expect(res.requestId).toBe('req-uuid-1234');
    expect(res.provider).toBe('MOCK_PROVIDER');
  });

  it('3. Handles missing recipient consent failure from Edge Function', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: { access_token: 'mock-valid-jwt-token' } },
    });

    (supabase.functions.invoke as any).mockResolvedValue({
      data: {
        success: false,
        error: 'Consent required: Recipient has not opted in or verified consent',
        code: 'RECIPIENT_CONSENT_MISSING',
      },
      error: null,
    });

    const res = await smsService.dispatchEmergencySms({
      scenario: 'EMERGENCY_CONTACT_ALERT',
      recipientPhone: '+919999999999',
      templateId: 'EMERGENCY_CONTACT_SOS',
      templateParameters: {},
    });

    expect(res.success).toBe(false);
    expect(res.error).toContain('Consent required');
  });

  it('4. Handles rate limit rejection (HTTP 429) from Edge Function', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: { access_token: 'mock-valid-jwt-token' } },
    });

    (supabase.functions.invoke as any).mockResolvedValue({
      data: {
        success: false,
        error: 'Rate limit exceeded for recipient phone. Maximum 3 alerts per 10-minute window.',
        code: 'RATE_LIMIT_EXCEEDED',
      },
      error: null,
    });

    const res = await smsService.dispatchEmergencySms({
      scenario: 'EMERGENCY_CONTACT_ALERT',
      recipientPhone: '+919876543210',
      templateId: 'EMERGENCY_CONTACT_SOS',
      templateParameters: {},
    });

    expect(res.success).toBe(false);
    expect(res.error).toContain('Rate limit exceeded');
  });

  it('5. Handles upstream provider carrier rejection gracefully', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: { access_token: 'mock-valid-jwt-token' } },
    });

    (supabase.functions.invoke as any).mockResolvedValue({
      data: {
        success: false,
        status: 'FAILED',
        error: 'Upstream gateway returned HTTP 502: Provider rejection',
      },
      error: null,
    });

    const res = await smsService.dispatchEmergencySms({
      scenario: 'OPERATOR_INCIDENT_DISPATCH',
      recipientPhone: '+919876540000',
      templateId: 'OPERATOR_FIELD_DISPATCH',
      templateParameters: {},
    });

    expect(res.success).toBe(false);
    expect(res.status).toBe('FAILED');
    expect(res.error).toContain('Provider rejection');
  });

  it('6. Handles network timeout or connection failure during Edge Function invoke', async () => {
    (supabase.auth.getSession as any).mockResolvedValue({
      data: { session: { access_token: 'mock-valid-jwt-token' } },
    });

    (supabase.functions.invoke as any).mockRejectedValue(
      new Error('Failed to fetch from backend Edge Function: Connection reset')
    );

    const res = await smsService.dispatchEmergencySms({
      scenario: 'PUBLIC_WARNING_BROADCAST',
      recipientPhone: '+919876543210',
      templateId: 'PUBLIC_SAFETY_WARNING',
      templateParameters: {},
    });

    expect(res.success).toBe(false);
    expect(res.error).toContain('Connection reset');
  });

  it('7. Queries recipient consent record correctly from database', async () => {
    const mockSelect = vi.fn().mockReturnThis();
    const mockEq = vi.fn().mockReturnThis();
    const mockSingle = vi.fn().mockResolvedValue({
      data: {
        id: 'pref-123',
        phone_number: '+919876543210',
        has_emergency_contact_consent: true,
        has_public_warning_consent: false,
        verification_status: 'VERIFIED',
      },
      error: null,
    });

    (supabase.from as any).mockReturnValue({
      select: mockSelect,
      eq: mockEq,
      single: mockSingle,
    });

    const consent = await smsService.getRecipientConsent('+919876543210');
    expect(consent).not.toBeNull();
    expect(consent?.has_emergency_contact_consent).toBe(true);
    expect(consent?.verification_status).toBe('VERIFIED');
  });

  it('8. Successfully records / updates operator-verified recipient consent', async () => {
    const mockUpsert = vi.fn().mockResolvedValue({
      error: null,
    });

    (supabase.from as any).mockReturnValue({
      upsert: mockUpsert,
    });

    const result = await smsService.recordRecipientConsent(
      '+919876543210',
      true,
      true,
      'OPERATOR_VERIFIED'
    );

    expect(result.success).toBe(true);
    expect(mockUpsert).toHaveBeenCalledWith(
      expect.objectContaining({
        phone_number: '+919876543210',
        has_emergency_contact_consent: true,
        has_public_warning_consent: true,
        verification_status: 'VERIFIED',
      }),
      { onConflict: 'phone_number' }
    );
  });
});
