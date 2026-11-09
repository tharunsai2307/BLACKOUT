// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /emergency-alerts
// Implements:
// 1. Authenticated & Guest SOS Ingestion with Redis Rate-Limiting & Idempotency
// 2. Ephemeral Gateway Presence Tracking (Upstash Redis)
// 3. Cached Aggregate Dashboard Statistics (Sub-10ms response)
// 4. Login Attempt Abuse Protection Endpoint
// 5. Fail-Safe Circuit Breaker (Zero SOS alert loss on Redis outage)

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";
import { redis } from "../_shared/redis.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gateway-device-id, x-network-type",
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
  const clientIp = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown-ip";

  // Initialize Supabase Admin Client using Service Role Key
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  try {
    // ------------------------------------------------------------------------
    // 1. Health Check Endpoint: GET /health or /emergency-alerts/health
    // ------------------------------------------------------------------------
    if (req.method === "GET" && (path.endsWith("/health") || path.endsWith("/emergency-alerts/health"))) {
      const redisStatus = redis.isReady() ? "UPSTASH_REDIS_CONNECTED" : "REDIS_CIRCUIT_BREAKER_STANDBY";
      return new Response(
        JSON.stringify({
          status: "HEALTHY",
          service: "blackout-emergency-gateway",
          redis: redisStatus,
          timestamp: new Date().toISOString(),
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ------------------------------------------------------------------------
    // 2. Cached Dashboard Stats: GET /emergency-alerts/stats
    // ------------------------------------------------------------------------
    if (req.method === "GET" && path.endsWith("/stats")) {
      // 2.1 Check Upstash Redis cache first (TTL 30s)
      const cached = await redis.cacheGet<any>("dashboard:stats");
      if (cached) {
        return new Response(JSON.stringify({ ...cached, cached: true, source: "upstash-redis" }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "HIT" },
        });
      }

      // 2.2 Cache miss: Aggregate directly via PostgreSQL RPC
      const { data: statsData, error: rpcError } = await supabase.rpc("get_incident_stats_summary");
      if (rpcError) {
        throw rpcError;
      }

      // 2.3 Store in Redis for 30 seconds
      if (statsData) {
        await redis.cacheSet("dashboard:stats", statsData, 30);
      }

      return new Response(JSON.stringify({ ...statsData, cached: false, source: "postgresql" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "MISS" },
      });
    }

    // ------------------------------------------------------------------------
    // 3. Ephemeral Gateway Heartbeat: POST /emergency-alerts/gateway-heartbeat
    // ------------------------------------------------------------------------
    if (req.method === "POST" && path.endsWith("/gateway-heartbeat")) {
      const body = await req.json();
      const gatewayDeviceId = body.gatewayDeviceId || req.headers.get("x-gateway-device-id");
      if (!gatewayDeviceId) {
        return new Response(JSON.stringify({ error: "Missing gatewayDeviceId" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const registered = await redis.registerGatewayPresence(
        gatewayDeviceId,
        {
          networkType: body.networkType || req.headers.get("x-network-type") || "Cellular/WiFi",
          ip: clientIp,
          appVersion: body.appVersion || "1.0",
        },
        180 // 3 minutes TTL
      );

      return new Response(
        JSON.stringify({
          status: "ACK",
          gatewayDeviceId,
          active: registered,
          ttlSeconds: 180,
          timestamp: Date.now(),
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ------------------------------------------------------------------------
    // 4. Auth Rate Limiting Helper: POST /emergency-alerts/auth-rate-limit
    // ------------------------------------------------------------------------
    if (req.method === "POST" && path.endsWith("/auth-rate-limit")) {
      const body = await req.json();
      const email = (body.email || "").trim().toLowerCase();
      const action = body.action || "check"; // 'check' | 'record_failure' | 'clear'
      const keyId = email || clientIp;

      if (action === "record_failure") {
        const result = await redis.checkRateLimit("login:failed", keyId, 5, 900); // 5 attempts per 15 min
        return new Response(
          JSON.stringify({
            allowed: result.allowed,
            remainingAttempts: result.remaining,
            retryAfter: result.retryAfterSeconds,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      } else if (action === "clear") {
        await redis.del(`ratelimit:login:failed:${keyId}`);
        return new Response(JSON.stringify({ cleared: true }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      } else {
        // Just inspect remaining
        const failedCount = (await redis.get<number>(`ratelimit:login:failed:${keyId}`)) || 0;
        const allowed = failedCount < 5;
        return new Response(
          JSON.stringify({
            allowed,
            failedAttempts: failedCount,
            maxAllowed: 5,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
    }

    // ------------------------------------------------------------------------
    // 5. Ingestion Endpoint: POST /emergency-alerts
    // ------------------------------------------------------------------------
    if (req.method === "POST") {
      const payload: EmergencyAlertPayload = await req.json();

      // Protocol & UUID Validation
      if (payload.protocolVersion !== 1) {
        return new Response(
          JSON.stringify({ error: "UNSUPPORTED_PROTOCOL_VERSION", message: `Expected protocol 1, got ${payload.protocolVersion}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (!payload.messageId || !uuidRegex.test(payload.messageId)) {
        return new Response(
          JSON.stringify({ error: "INVALID_MESSAGE_ID", message: "messageId must be a valid RFC 4122 UUID" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (!VALID_CATEGORIES.has(payload.category)) {
        return new Response(
          JSON.stringify({ error: "INVALID_CATEGORY", message: `Invalid emergency category: ${payload.category}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (!payload.description || payload.description.trim().length === 0 || payload.description.length > 1000) {
        return new Response(
          JSON.stringify({ error: "INVALID_DESCRIPTION", message: "Description must be between 1 and 1000 characters" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Coordinates Validation
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

      const originalSender = payload.originalSenderDeviceId || payload.senderDeviceId || "UNKNOWN_DEV";
      const gatewayDevice = payload.gatewayDeviceId || req.headers.get("x-gateway-device-id") || "GATEWAY-UNKNOWN";
      const priority = VALID_PRIORITIES.has(payload.priority) ? payload.priority : "MODERATE";
      const hopCount = Math.min(Math.max(payload.hopCount || 0, 0), 7);
      const now = Date.now();

      // ----------------------------------------------------------------------
      // 5.1 Redis-Backed Rate Limiting (Distinguish Guest vs Authenticated)
      // ----------------------------------------------------------------------
      let isAuthenticated = false;
      let userId: string | null = null;

      const authHeader = req.headers.get("Authorization");
      if (authHeader && authHeader.startsWith("Bearer ")) {
        const token = authHeader.replace("Bearer ", "").trim();
        const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
        // If the token is not the anonymous key, verify it as a user JWT
        if (token && token !== anonKey) {
          try {
            const { data: userData } = await supabase.auth.getUser(token);
            if (userData?.user) {
              isAuthenticated = true;
              userId = userData.user.id;
            }
          } catch {
            // Treat as guest
          }
        }
      }

      // Apply endpoint-specific Redis rate limit:
      // Authenticated users: 60 SOS / minute
      // Guest beacons: 15 SOS / minute per gateway device / IP
      const rateLimitCategory = isAuthenticated ? "sos:auth" : "sos:guest";
      const rateLimitIdentifier = isAuthenticated && userId ? userId : `${gatewayDevice}:${clientIp}`;
      const rateLimitMax = isAuthenticated ? 60 : 15;
      const rateLimitWindow = 60; // 60 seconds

      const rateLimitResult = await redis.checkRateLimit(
        rateLimitCategory,
        rateLimitIdentifier,
        rateLimitMax,
        rateLimitWindow
      );

      if (!rateLimitResult.allowed) {
        return new Response(
          JSON.stringify({
            error: "RATE_LIMIT_EXCEEDED",
            message: "SOS alert submission rate limit exceeded. Please wait before retrying.",
            retryAfter: rateLimitResult.retryAfterSeconds,
          }),
          {
            status: 429,
            headers: {
              ...corsHeaders,
              "Content-Type": "application/json",
              "Retry-After": String(rateLimitResult.retryAfterSeconds),
              "X-RateLimit-Limit": String(rateLimitResult.limit),
              "X-RateLimit-Remaining": String(rateLimitResult.remaining),
              "X-RateLimit-Reset": String(rateLimitResult.reset),
            },
          }
        );
      }

      // Compute canonical payload hash
      const computedHash = await computePayloadHash({
        messageId: payload.messageId,
        originalSenderDeviceId: originalSender,
        category: payload.category,
        description: payload.description,
        createdAt: payload.createdAt || now,
      });

      // ----------------------------------------------------------------------
      // 5.2 Redis Short-Lived Idempotency Lock
      // ----------------------------------------------------------------------
      // Provides sub-millisecond deduplication during high-density mesh gossip bursts
      const idempotencyResult = await redis.checkAndSetIdempotency(payload.messageId, gatewayDevice, 86400);

      if (!idempotencyResult.isNew && !idempotencyResult.fallbackUsed) {
        // Fast-path duplicate accepted: check if we have cached backendRef
        const cachedRef = await redis.get<string>(`alert:ref:${payload.messageId}`);
        if (cachedRef) {
          return new Response(
            JSON.stringify({
              status: "DUPLICATE_ACCEPTED",
              message: "Emergency alert already recorded in central database",
              messageId: payload.messageId,
              backendReference: cachedRef,
              ingestionStatus: "ACCEPTED",
              source: "upstash-redis-idempotency",
            }),
            { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }

      // ----------------------------------------------------------------------
      // 5.3 PostgreSQL Source of Truth Persistence
      // ----------------------------------------------------------------------
      const { data: existingAlert, error: selectError } = await supabase
        .from("emergency_alerts")
        .select("id, message_id, payload_hash, ingestion_status, created_at")
        .eq("message_id", payload.messageId)
        .maybeSingle();

      if (selectError) {
        throw selectError;
      }

      if (existingAlert) {
        // Store in Redis to accelerate future duplicates from other mesh gateways
        await redis.set(`alert:ref:${payload.messageId}`, existingAlert.id, { ex: 86400 });

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

        // Log duplicate gateway encounter
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

      // Insert New Alert Record
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

      // ----------------------------------------------------------------------
      // 5.4 Post-Ingestion Operations (Redis Caching & Gateway Presence)
      // ----------------------------------------------------------------------
      // 1. Cache alert backend reference
      await redis.set(`alert:ref:${payload.messageId}`, newAlert.id, { ex: 86400 });

      // 2. Invalidate dashboard stats cache so operators immediately see the new alert
      await redis.cacheInvalidate("dashboard:stats");

      // 3. Register gateway online presence in Redis
      await redis.registerGatewayPresence(gatewayDevice, {
        networkType: req.headers.get("x-network-type") || "Mobile Gateway",
        ip: clientIp,
        hopCount,
      });

      // 4. Log gateway upload history in PostgreSQL
      await supabase.from("gateway_upload_history").insert({
        message_id: payload.messageId,
        gateway_device_id: gatewayDevice,
        upload_status: "SUCCESS",
        response_reference: newAlert.id,
      });

      // 5. Record audit event
      await supabase.from("emergency_alert_events").insert({
        message_id: payload.messageId,
        actor_id: gatewayDevice,
        event_type: "ALERT_INGESTED_VIA_GATEWAY",
        metadata: {
          originalSender,
          hops: hopCount,
          priority,
          isAuthenticated,
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
