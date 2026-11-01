// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /notifications-sms
// Provider-Independent Server-Side Emergency SMS Dispatch & Audit Engine

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-idempotency-key",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

// ============================================================================
// DATA MODELS & ENUMS
// ============================================================================

export type SmsDeliveryStatus =
  | "QUEUED"
  | "SUBMITTED"
  | "PROVIDER_ACCEPTED"
  | "DELIVERED"
  | "FAILED"
  | "UNKNOWN";

export type SmsScenario =
  | "EMERGENCY_CONTACT_ALERT"
  | "OPERATOR_INCIDENT_DISPATCH"
  | "PUBLIC_WARNING_BROADCAST";

export interface SmsSendPayload {
  scenario: SmsScenario;
  recipientPhone: string; // E.164 format (+919876543210)
  templateId: string;
  templateParameters: Record<string, string>;
  incidentId?: string;
  warningId?: string;
  idempotencyKey?: string;
}

export interface SmsProviderResult {
  status: SmsDeliveryStatus;
  providerReference?: string;
  errorCode?: string;
  errorMessage?: string;
  latencyMs: number;
}

// ============================================================================
// PROVIDER ABSTRACTION INTERFACE
// ============================================================================

export interface ISmsProvider {
  name: string;
  send(recipientPhone: string, messageBody: string, dltTemplateId?: string): Promise<SmsProviderResult>;
}

// 1. Mock / Development Provider for Automated Tests & Dry-Runs
export class MockSmsProvider implements ISmsProvider {
  name = "MOCK_PROVIDER";

  async send(recipientPhone: string, _messageBody: string): Promise<SmsProviderResult> {
    const start = Date.now();
    // Deterministic simulation based on recipient number
    if (recipientPhone.endsWith("0000")) {
      return {
        status: "FAILED",
        errorCode: "CARRIER_TEMPORARY_FAILURE",
        errorMessage: "Simulated carrier network drop",
        latencyMs: Date.now() - start,
      };
    }

    return {
      status: "PROVIDER_ACCEPTED",
      providerReference: `MOCK-${crypto.randomUUID().slice(0, 8).toUpperCase()}`,
      latencyMs: Date.now() - start + 25,
    };
  }
}

// 2. Legitimate Transactional Webhook Provider (e.g., Twilio / Indian DLT Gateway)
export class GenericWebhookSmsProvider implements ISmsProvider {
  name = "GENERIC_WEBHOOK";
  private webhookUrl: string;
  private apiKey: string;
  private senderId: string;

  constructor(webhookUrl: string, apiKey: string, senderId = "BLKOUT") {
    this.webhookUrl = webhookUrl;
    this.apiKey = apiKey;
    this.senderId = senderId;
  }

  async send(recipientPhone: string, messageBody: string, dltTemplateId?: string): Promise<SmsProviderResult> {
    const start = Date.now();
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 6000);

      const resp = await fetch(this.webhookUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${this.apiKey}`,
          "X-Sender-ID": this.senderId,
        },
        body: JSON.stringify({
          to: recipientPhone,
          message: messageBody,
          dltTemplateId: dltTemplateId || undefined,
        }),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - start;

      if (!resp.ok) {
        return {
          status: "FAILED",
          errorCode: `HTTP_${resp.status}`,
          errorMessage: `Provider returned status ${resp.status}`,
          latencyMs,
        };
      }

      const data = await resp.json().catch(() => ({}));
      return {
        status: "PROVIDER_ACCEPTED",
        providerReference: data.id || data.messageId || "TX-ACCEPTED",
        latencyMs,
      };
    } catch (err: any) {
      return {
        status: "FAILED",
        errorCode: err.name === "AbortError" ? "TIMEOUT" : "NETWORK_ERROR",
        errorMessage: "Failed to communicate with SMS upstream gateway",
        latencyMs: Date.now() - start,
      };
    }
  }
}

// 3. Official Twilio REST API SMS Provider
export class TwilioSmsProvider implements ISmsProvider {
  name = "TWILIO";
  private accountSid: string;
  private apiKeySid: string;
  private apiSecret: string;
  private fromNumber: string;

  constructor(accountSid: string, apiKeySid: string, apiSecret: string, fromNumber: string) {
    this.accountSid = accountSid;
    this.apiKeySid = apiKeySid;
    this.apiSecret = apiSecret;
    this.fromNumber = fromNumber;
  }

  async send(recipientPhone: string, messageBody: string): Promise<SmsProviderResult> {
    const start = Date.now();
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 8000);

      const endpoint = `https://api.twilio.com/2010-04-01/Accounts/${this.accountSid}/Messages.json`;
      const basicAuth = btoa(`${this.apiKeySid}:${this.apiSecret}`);

      const formData = new URLSearchParams();
      formData.append("To", recipientPhone);
      formData.append("From", this.fromNumber);
      formData.append("Body", messageBody);

      const resp = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Authorization": `Basic ${basicAuth}`,
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: formData.toString(),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - start;
      const data = await resp.json().catch(() => ({}));

      if (!resp.ok) {
        return {
          status: "FAILED",
          errorCode: data.code ? `TWILIO_${data.code}` : `HTTP_${resp.status}`,
          errorMessage: data.message || `Twilio dispatch failed with HTTP ${resp.status}`,
          latencyMs,
        };
      }

      return {
        status: "PROVIDER_ACCEPTED",
        providerReference: data.sid || "TWILIO-ACCEPTED",
        latencyMs,
      };
    } catch (err: any) {
      return {
        status: "FAILED",
        errorCode: err.name === "AbortError" ? "TIMEOUT" : "NETWORK_ERROR",
        errorMessage: err.message || "Failed to communicate with Twilio upstream gateway",
        latencyMs: Date.now() - start,
      };
    }
  }
}

// ============================================================================
// APPROVED EMERGENCY TEMPLATE CATALOG (DLT-COMPLIANT)
// ============================================================================

export const APPROVED_TEMPLATES: Record<string, { body: string; dltTemplateId: string }> = {
  EMERGENCY_CONTACT_SOS: {
    dltTemplateId: "1407161234567890123",
    body: "BLACKOUT ALERT: Your emergency contact {name} reported an SOS ({category}) at GPS {coordinates}. Stand by for official rescue updates. Ref: {refId}",
  },
  OPERATOR_FIELD_DISPATCH: {
    dltTemplateId: "1407161234567890124",
    body: "BLACKOUT DISPATCH: Response team {teamName} assigned to Incident #{incidentId} ({category}) at {coordinates}. Priority: {priority}.",
  },
  PUBLIC_SAFETY_WARNING: {
    dltTemplateId: "1407161234567890125",
    body: "CIVIL DEFENSE WARNING: {title}. {description}. Issued by {authority}. Effective until {expiresAt}. Take immediate shelter.",
  },
};

export function renderTemplate(templateId: string, params: Record<string, string>): { text: string; dltTemplateId: string } | null {
  const tpl = APPROVED_TEMPLATES[templateId];
  if (!tpl) return null;

  let text = tpl.body;
  for (const [key, val] of Object.entries(params)) {
    // Sanitize parameter to prevent line injection or control characters
    const sanitizedVal = String(val).replace(/[\r\n\t]/g, " ").slice(0, 80);
    text = text.replace(new RegExp(`\\{${key}\\}`, "g"), sanitizedVal);
  }

  // Ensure character bounds for single/double SMS segment
  if (text.length > 320) {
    text = text.slice(0, 317) + "...";
  }

  return { text, dltTemplateId: tpl.dltTemplateId };
}

// Mask phone for safe audit logs
function maskPhone(phone: string): string {
  if (phone.length <= 4) return "****";
  return phone.slice(0, 3) + "******" + phone.slice(-4);
}

// ============================================================================
// MAIN HTTP HANDLER
// ============================================================================

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Authenticate Request
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authError } = await supabase.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 2. Authorize Role (SUPER_ADMIN, CONTROL_ROOM_OPERATOR, FIELD_RESPONSE_COORDINATOR)
    const { data: userRoleData } = await supabase
      .from("user_roles")
      .select("role")
      .eq("user_id", user.id)
      .single();

    const role = userRoleData?.role || "VIEWER";
    const PERMITTED_ROLES = new Set(["SUPER_ADMIN", "CONTROL_ROOM_OPERATOR", "FIELD_RESPONSE_COORDINATOR"]);
    if (!PERMITTED_ROLES.has(role)) {
      return new Response(JSON.stringify({ error: "Forbidden: Operator role not authorized to initiate emergency SMS dispatch" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed. Use POST." }), {
        status: 405,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 3. Parse and Validate Request Payload
    const payload: SmsSendPayload = await req.json().catch(() => null);
    if (!payload || !payload.scenario || !payload.recipientPhone || !payload.templateId) {
      return new Response(JSON.stringify({ error: "Missing required fields: scenario, recipientPhone, templateId" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Validate E.164 Phone Number Format
    const E164_REGEX = /^\+[1-9]\d{6,14}$/;
    if (!E164_REGEX.test(payload.recipientPhone)) {
      return new Response(JSON.stringify({ error: "Invalid phone number. Must follow international E.164 format (e.g. +919876543210)" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 4. Verify Recipient Consent in Database
    const { data: consentData } = await supabase
      .from("sms_recipient_preferences")
      .select("*")
      .eq("phone_number", payload.recipientPhone)
      .single();

    const isEmergencyContact = payload.scenario === "EMERGENCY_CONTACT_ALERT";
    const isPublicWarning = payload.scenario === "PUBLIC_WARNING_BROADCAST";

    const hasConsent = consentData && (
      (isEmergencyContact && consentData.has_emergency_contact_consent) ||
      (isPublicWarning && consentData.has_public_warning_consent) ||
      (payload.scenario === "OPERATOR_INCIDENT_DISPATCH" && consentData.verification_status === "VERIFIED")
    );

    if (!hasConsent && role !== "SUPER_ADMIN") {
      return new Response(JSON.stringify({
        error: "Consent required: Recipient has not opted in or verified consent for this SMS notification scenario",
        code: "RECIPIENT_CONSENT_MISSING",
        maskedPhone: maskPhone(payload.recipientPhone),
      }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 5. Template Resolution
    const rendered = renderTemplate(payload.templateId, payload.templateParameters || {});
    if (!rendered) {
      return new Response(JSON.stringify({ error: `Unknown or unapproved SMS template: ${payload.templateId}` }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 6. Idempotency Check
    const idempotencyKey = payload.idempotencyKey || req.headers.get("x-idempotency-key") || `${payload.scenario}-${payload.incidentId || payload.warningId || 'gen'}-${payload.recipientPhone}-${Date.now()}`;
    const { data: existingRequest } = await supabase
      .from("sms_notification_requests")
      .select("*")
      .eq("idempotency_key", idempotencyKey)
      .single();

    if (existingRequest) {
      return new Response(JSON.stringify({
        message: "Duplicate request identified via idempotency key; returning existing status",
        requestId: existingRequest.request_id,
        status: existingRequest.status,
      }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 7. Enforce Rate Limiting (Max 3 messages to same number in 10 minutes)
    const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
    const { count: recentAttemptsCount } = await supabase
      .from("sms_notification_requests")
      .select("id", { count: "exact", head: true })
      .eq("recipient_phone", payload.recipientPhone)
      .gte("created_at", tenMinutesAgo);

    if ((recentAttemptsCount ?? 0) >= 3 && role !== "SUPER_ADMIN") {
      return new Response(JSON.stringify({
        error: "Rate limit exceeded for recipient phone. Maximum 3 alerts per 10-minute window.",
        code: "RATE_LIMIT_EXCEEDED",
      }), {
        status: 429,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 8. Select and Instantiate Provider
    const twilioAccountSid = Deno.env.get("TWILIO_ACCOUNT_SID") || Deno.env.get("TWILIO_API_KEY_SID");
    const twilioApiKeySid = Deno.env.get("TWILIO_API_KEY_SID");
    const twilioApiSecret = Deno.env.get("TWILIO_API_SECRET");
    const twilioFromNumber = Deno.env.get("TWILIO_FROM_NUMBER") || "+15005550006";

    const webhookUrl = Deno.env.get("SMS_GATEWAY_URL");
    const webhookKey = Deno.env.get("SMS_GATEWAY_API_KEY");
    let provider: ISmsProvider;

    if (twilioApiKeySid && twilioApiSecret && twilioAccountSid) {
      provider = new TwilioSmsProvider(twilioAccountSid, twilioApiKeySid, twilioApiSecret, twilioFromNumber);
    } else if (webhookUrl && webhookKey) {
      provider = new GenericWebhookSmsProvider(webhookUrl, webhookKey);
    } else {
      // Safe fallback to deterministic Mock Provider for non-production environments
      provider = new MockSmsProvider();
    }

    // 9. Record Notification Request as QUEUED
    const { data: requestRecord, error: insertError } = await supabase
      .from("sms_notification_requests")
      .insert({
        idempotency_key: idempotencyKey,
        scenario: payload.scenario,
        incident_id: payload.incidentId || null,
        warning_id: payload.warningId || null,
        recipient_phone: payload.recipientPhone,
        template_id: payload.templateId,
        template_parameters: payload.templateParameters || {},
        sanitized_message_preview: rendered.text.slice(0, 160),
        status: "QUEUED",
        priority: payload.scenario === "EMERGENCY_CONTACT_ALERT" ? "CRITICAL" : "HIGH",
        requested_by: user.id,
        requested_by_role: role,
        attempts_count: 1,
        last_attempt_at: new Date().toISOString(),
      })
      .select()
      .single();

    if (insertError) {
      return new Response(JSON.stringify({ error: `Database failure: ${insertError.message}` }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 10. Execute Delivery via Provider
    const result = await provider.send(payload.recipientPhone, rendered.text, rendered.dltTemplateId);

    // 11. Log Delivery Attempt
    await supabase.from("sms_delivery_attempts").insert({
      notification_request_id: requestRecord.id,
      provider_name: provider.name,
      attempt_number: 1,
      status: result.status,
      provider_reference: result.providerReference || null,
      error_code: result.errorCode || null,
      error_message: result.errorMessage || null,
      latency_ms: result.latencyMs,
    });

    // 12. Update Request Status
    await supabase
      .from("sms_notification_requests")
      .update({
        status: result.status,
        completed_at: result.status === "PROVIDER_ACCEPTED" ? new Date().toISOString() : null,
        failure_reason: result.errorMessage || null,
        updated_at: new Date().toISOString(),
      })
      .eq("id", requestRecord.id);

    return new Response(JSON.stringify({
      success: result.status === "PROVIDER_ACCEPTED",
      requestId: requestRecord.request_id,
      status: result.status,
      provider: provider.name,
      providerReference: result.providerReference,
      latencyMs: result.latencyMs,
      maskedRecipient: maskPhone(payload.recipientPhone),
      messagePreview: rendered.text,
    }), {
      status: result.status === "PROVIDER_ACCEPTED" ? 200 : 502,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (err: any) {
    return new Response(JSON.stringify({
      error: "Internal server error during SMS notification processing",
      details: err.message,
    }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});

