// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /emergency-alerts
// Handles authenticated/validated ingestion of emergency SOS alerts from mobile gateways

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gateway-device-id",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

interface EmergencyAlertPayload {
  protocolVersion: number;
  messageType: string;
  messageId: string;
  originalSenderDeviceId: string;
  currentSenderDeviceId?: string;
  senderDeviceId?: string;
  gatewayDeviceId: string;
  category: string;
  description: string;
  priority: string;
  latitude?: number | null;
  longitude?: number | null;
  accuracyMeters?: number | null;
  locationTimestamp?: number | null;
  locationStatus?: string;
  createdAt: number;
  expiresAt: number;
  hopCount: number;
  payloadHash?: string;
}

const VALID_CATEGORIES = new Set([
  "Medical Emergency",
  "Road Accident",
  "Trapped / Stranded",
  "Flood",
  "Fire",
  "Natural Disaster",
  "Personal Safety",
  "Other",
]);

const VALID_PRIORITIES = new Set(["CRITICAL", "HIGH", "MODERATE", "LOW"]);

// SHA-256 Canonical payload hash generator
async function computePayloadHash(data: {
  messageId: string;
  originalSenderDeviceId: string;
  category: string;
  description: string;
  createdAt: number;
}): Promise<string> {
  const canonical = `${data.messageId}|${data.originalSenderDeviceId}|${data.category}|${data.description}|${data.createdAt}`;
  const msgUint8 = new TextEncoder().encode(canonical);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const path = url.pathname;

  // Initialize Supabase Admin Client using Service Role Key
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  try {
    // Health Check Endpoint
    if (req.method === "GET" && path.endsWith("/health")) {
      return new Response(
        JSON.stringify({ status: "HEALTHY", timestamp: new Date().toISOString(), service: "blackout-emergency-gateway" }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Ingestion Endpoint: POST /emergency-alerts
    if (req.method === "POST") {
      const payload: EmergencyAlertPayload = await req.json();

      // 1. Protocol Validation
      if (payload.protocolVersion !== 1) {
        return new Response(
          JSON.stringify({ error: "UNSUPPORTED_PROTOCOL_VERSION", message: `Expected protocol 1, got ${payload.protocolVersion}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // 2. UUID Validation
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (!payload.messageId || !uuidRegex.test(payload.messageId)) {
        return new Response(
          JSON.stringify({ error: "INVALID_MESSAGE_ID", message: "messageId must be a valid RFC 4122 UUID" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // 3. Category Validation
      if (!VALID_CATEGORIES.has(payload.category)) {
        return new Response(
          JSON.stringify({ error: "INVALID_CATEGORY", message: `Invalid emergency category: ${payload.category}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // 4. Description Validation
      if (!payload.description || payload.description.trim().length === 0 || payload.description.length > 1000) {
        return new Response(
          JSON.stringify({ error: "INVALID_DESCRIPTION", message: "Description must be between 1 and 1000 characters" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // 5. Coordinates Validation
      if (payload.latitude !== undefined && payload.latitude !== null) {
        if (payload.latitude < -90.0 || payload.latitude > 90.0) {
          return new Response(
            JSON.stringify({ error: "INVALID_LATITUDE", message: "Latitude must be within [-90, 90]" }),
            { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }
      if (payload.longitude !== undefined && payload.longitude !== null) {
        if (payload.longitude < -180.0 || payload.longitude > 180.0) {
          return new Response(
            JSON.stringify({ error: "INVALID_LONGITUDE", message: "Longitude must be within [-180, 180]" }),
            { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }

      // 6. Expiry & Hop Count Check
      const now = Date.now();
      if (payload.expiresAt && payload.expiresAt <= now) {
        return new Response(
          JSON.stringify({ error: "ALERT_EXPIRED", message: "Emergency alert has expired" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const hopCount = payload.hopCount || 0;
      if (hopCount < 0 || hopCount > 7) {
        return new Response(
          JSON.stringify({ error: "HOP_LIMIT_EXCEEDED", message: "Hop count exceeds maximum allowable limit of 7" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const originalSender = payload.originalSenderDeviceId || payload.senderDeviceId || "UNKNOWN_DEV";
      const gatewayDevice = payload.gatewayDeviceId || "GATEWAY-UNKNOWN";
      const priority = VALID_PRIORITIES.has(payload.priority) ? payload.priority : "MODERATE";

      // Compute canonical payload hash
      const computedHash = await computePayloadHash({
        messageId: payload.messageId,
        originalSenderDeviceId: originalSender,
        category: payload.category,
        description: payload.description,
        createdAt: payload.createdAt,
      });

      // 7. Check for Existing Alert (Idempotency and Conflict Detection)
      const { data: existingAlert, error: selectError } = await supabase
        .from("emergency_alerts")
        .select("id, message_id, payload_hash, ingestion_status, created_at")
        .eq("message_id", payload.messageId)
        .maybeSingle();

      if (selectError) {
        throw selectError;
      }

      if (existingAlert) {
        // Check hash conflict
        if (existingAlert.payload_hash && existingAlert.payload_hash !== computedHash) {
          return new Response(
            JSON.stringify({
              error: "CONFLICTING_PAYLOAD",
              message: "An alert with this messageId already exists with different content hash",
              messageId: payload.messageId,
            }),
            { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        // Log duplicate gateway upload
        await supabase.from("gateway_upload_history").insert({
          message_id: payload.messageId,
          gateway_device_id: gatewayDevice,
          upload_status: "DUPLICATE",
          response_reference: existingAlert.id,
        });

        return new Response(
          JSON.stringify({
            status: "DUPLICATE_ACCEPTED",
            message: "Emergency alert already recorded in central database",
            messageId: payload.messageId,
            backendReference: existingAlert.id,
            ingestionStatus: existingAlert.ingestion_status,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // 8. Insert New Record Transactionally
      const { data: newAlert, error: insertError } = await supabase
        .from("emergency_alerts")
        .insert({
          message_id: payload.messageId,
          original_sender_device_id: originalSender,
          gateway_device_id: gatewayDevice,
          category: payload.category,
          description: payload.description,
          latitude: payload.latitude ?? null,
          longitude: payload.longitude ?? null,
          accuracy_meters: payload.accuracyMeters ?? null,
          location_timestamp: payload.locationTimestamp ? new Date(payload.locationTimestamp).toISOString() : null,
          created_at: new Date(payload.createdAt || now).toISOString(),
          expires_at: new Date(payload.expiresAt || now + 48 * 3600 * 1000).toISOString(),
          hop_count: hopCount,
          priority: priority,
          ingestion_status: "ACCEPTED",
          operator_status: "NEW",
          protocol_version: 1,
          payload_hash: computedHash,
        })
        .select("id, message_id, created_at")
        .single();

      if (insertError) {
        throw insertError;
      }

      // Log successful upload attempt
      await supabase.from("gateway_upload_history").insert({
        message_id: payload.messageId,
        gateway_device_id: gatewayDevice,
        upload_status: "SUCCESS",
        response_reference: newAlert.id,
      });

      // Record audit event
      await supabase.from("emergency_alert_events").insert({
        message_id: payload.messageId,
        actor_id: gatewayDevice,
        event_type: "ALERT_INGESTED_VIA_GATEWAY",
        metadata: {
          originalSender: originalSender,
          hops: hopCount,
          priority: priority,
        },
      });

      return new Response(
        JSON.stringify({
          status: "SUCCESS",
          message: "Emergency alert successfully ingested and persisted in central database",
          messageId: payload.messageId,
          backendReference: newAlert.id,
          receivedAt: new Date().toISOString(),
        }),
        { status: 201, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(JSON.stringify({ error: "NOT_FOUND" }), {
      status: 404,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: "INTERNAL_SERVER_ERROR", message: err.message || "An unexpected error occurred" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
