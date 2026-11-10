// BLACKOUT Emergency Operations Portal
// SMS Notification & Recipient Consent Service

import { supabase } from './supabase';

export type SmsDeliveryStatus =
  | 'QUEUED'
  | 'SUBMITTED'
  | 'PROVIDER_ACCEPTED'
  | 'DELIVERED'
  | 'FAILED'
  | 'UNKNOWN';

export type SmsScenario =
  | 'EMERGENCY_CONTACT_ALERT'
  | 'OPERATOR_INCIDENT_DISPATCH'
  | 'PUBLIC_WARNING_BROADCAST';

export interface SmsNotificationRecord {
  id: string;
  request_id: string;
  idempotency_key: string;
  scenario: SmsScenario;
  incident_id?: string | null;
  warning_id?: string | null;
  recipient_phone: string;
  template_id: string;
  template_parameters: Record<string, string>;
  sanitized_message_preview: string;
  status: SmsDeliveryStatus;
  priority: string;
  requested_by_role: string;
  attempts_count: number;
  failure_reason?: string | null;
  created_at: string;
  completed_at?: string | null;
  delivery_attempts?: SmsDeliveryAttemptRecord[];
}

export interface SmsDeliveryAttemptRecord {
  id: string;
  provider_name: string;
  attempt_number: number;
  status: SmsDeliveryStatus;
  provider_reference?: string | null;
  error_code?: string | null;
  error_message?: string | null;
  latency_ms?: number | null;
  attempted_at: string;
}

export interface SmsSendRequest {
  scenario: SmsScenario;
  recipientPhone: string;
  templateId: string;
  templateParameters: Record<string, string>;
  incidentId?: string;
  warningId?: string;
  idempotencyKey?: string;
}

export interface RecipientConsentRecord {
  id: string;
  phone_number: string;
  has_emergency_contact_consent: boolean;
  has_public_warning_consent: boolean;
  verification_status: 'UNVERIFIED' | 'VERIFIED' | 'REVOKED';
  consent_source: string;
  consent_timestamp?: string | null;
}

class SmsService {
  /**
   * Fetch SMS notification dispatch records and delivery attempts for an incident
   */
  async getIncidentSmsNotifications(incidentId: string): Promise<SmsNotificationRecord[]> {
    try {
      const { data, error } = await supabase
        .from('sms_notification_requests')
        .select(`
          *,
          delivery_attempts:sms_delivery_attempts(*)
        `)
        .eq('incident_id', incidentId)
        .order('created_at', { ascending: false });

      if (error) {
        console.warn('Failed to query incident SMS records:', error.message);
        return [];
      }
      return data || [];
    } catch (err) {
      console.warn('Error fetching incident SMS notifications:', err);
      return [];
    }
  }

  /**
   * Query recipient preferences and consent record
   */
  async getRecipientConsent(phone: string): Promise<RecipientConsentRecord | null> {
    try {
      const { data, error } = await supabase
        .from('sms_recipient_preferences')
        .select('*')
        .eq('phone_number', phone)
        .single();

      if (error && error.code !== 'PGRST116') {
        console.warn('Consent lookup error:', error.message);
      }
      return data || null;
    } catch {
      return null;
    }
  }

  /**
   * Grant / verify recipient consent (Operator action with audit justification)
   */
  async recordRecipientConsent(
    phone: string,
    hasEmergencyConsent: boolean,
    hasWarningConsent: boolean,
    source = 'OPERATOR_VERIFIED'
  ): Promise<{ success: boolean; error?: string }> {
    try {
      // 1. Try secure stored procedure RPC (SECURITY DEFINER)
      try {
        const { data: rpcData, error: rpcErr } = await supabase.rpc('record_recipient_consent', {
          p_phone_number: phone,
          p_has_emergency_consent: hasEmergencyConsent,
          p_has_warning_consent: hasWarningConsent,
          p_source: source,
        });
        if (!rpcErr && rpcData) {
          return { success: true };
        }
      } catch (rpcEx) {
        console.warn('record_recipient_consent RPC fallback to table upsert:', rpcEx);
      }

      // 2. Direct table upsert fallback
      const { error } = await supabase
        .from('sms_recipient_preferences')
        .upsert({
          phone_number: phone,
          has_emergency_contact_consent: hasEmergencyConsent,
          has_public_warning_consent: hasWarningConsent,
          verification_status: 'VERIFIED',
          consent_source: source,
          consent_timestamp: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        }, { onConflict: 'phone_number' });

      if (error) return { success: false, error: error.message };
      return { success: true };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  /**
   * Dispatch an authorized emergency SMS notification via Edge Function
   */
  async dispatchEmergencySms(payload: SmsSendRequest): Promise<{
    success: boolean;
    requestId?: string;
    status?: SmsDeliveryStatus;
    provider?: string;
    error?: string;
    messagePreview?: string;
  }> {
    try {
      const { data: sessionData } = await supabase.auth.getSession();
      const token = sessionData.session?.access_token;

      if (!token) {
        return { success: false, error: 'Unauthorized: Active operator session required' };
      }

      // Invoke the Edge Function: /notifications-sms
      const { data, error } = await supabase.functions.invoke('notifications-sms', {
        body: payload,
      });

      if (error) {
        return { success: false, error: error.message || 'SMS service invocation failed' };
      }

      return {
        success: data.success ?? false,
        requestId: data.requestId,
        status: data.status,
        provider: data.provider,
        error: data.error,
        messagePreview: data.messagePreview,
      };
    } catch (err: any) {
      return { success: false, error: err.message || 'Unexpected failure during SMS dispatch' };
    }
  }
}

export const smsService = new SmsService();

