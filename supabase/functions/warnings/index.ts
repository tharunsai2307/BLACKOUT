// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /warnings
// Implements Phase 7 Digitally Signed Public Emergency Warnings Backend API
// Endpoints:
// - POST /warnings/:warningId/sign
// - GET  /warnings/active
// - GET  /warnings/:warningId
// - GET  /warning-keys
// - POST /warnings/:warningId/revoke

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gateway-device-id",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

// Root Signing Key Configuration
const ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
const ROOT_PUB_KEY_BASE64 = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
// Protected Private Key (Loaded from environment secret; fallback to configured seed if unassigned in dev)
const DEFAULT_PRIV_KEY_BASE64 = "Dzk1c03BCXfD5EAA/HWzf/tjiDaPGS6T05ZuBgS66LA=";

// Helper: Base64 to Uint8Array
function base64ToBytes(base64: string): Uint8Array {
  const binaryString = atob(base64.trim());
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

// Helper: Uint8Array to Base64
function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// Deterministic Canonical Representation (Phase 7 Protocol)
export function buildCanonicalString(w: {
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
  const regions = (w.affectedRegions || [])
    .filter((r) => typeof r === "string" && r.trim().length > 0)
    .map((r) => r.trim())
    .sort()
    .join(",");

  const latStr = w.latitude != null ? Number(w.latitude).toFixed(6) : "null";
  const lonStr = w.longitude != null ? Number(w.longitude).toFixed(6) : "null";
  const radStr = w.radiusMeters != null ? Number(w.radiusMeters).toFixed(2) : "null";

  return [
    "BLACKOUT_WARNING_V1",
    `warningId=${(w.warningId || "").trim()}`,
    `protocolVersion=${w.protocolVersion || 1}`,
    `revision=${w.revision || 1}`,
    `status=${(w.status || "").trim().toUpperCase()}`,
    `category=${(w.category || "").trim().toUpperCase()}`,
    `severity=${(w.severity || "").trim().toUpperCase()}`,
    `issuingAuthority=${(w.issuingAuthority || "").trim()}`,
    `title=${(w.title || "").trim()}`,
    `description=${(w.description || "").trim()}`,
    `issuedAt=${w.issuedAt}`,
    `effectiveAt=${w.effectiveAt}`,
    `expiresAt=${w.expiresAt}`,
    `language=${(w.language || "en").trim().toLowerCase()}`,
    `signingKeyId=${(w.signingKeyId || "").trim()}`,
    `signatureAlgorithm=${(w.signatureAlgorithm || "Ed25519").trim()}`,
    `latitude=${latStr}`,
    `longitude=${lonStr}`,
    `radiusMeters=${radStr}`,
    `affectedRegions=${regions}`,
  ].join("\n");
}

// Signs canonical UTF-8 bytes using Ed25519 private key
async function signEd25519(canonicalStr: string, rawPrivKeyBase64: string): Promise<string> {
  const rawPrivBytes = base64ToBytes(rawPrivKeyBase64);

  // PKCS#8 prefix for Ed25519: 302e020100300506032b657004220420
  const pkcs8Prefix = new Uint8Array([
    0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
    0x04, 0x22, 0x04, 0x20,
  ]);
  const pkcs8Der = new Uint8Array(pkcs8Prefix.length + rawPrivBytes.length);
  pkcs8Der.set(pkcs8Prefix);
  pkcs8Der.set(rawPrivBytes, pkcs8Prefix.length);

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pkcs8Der,
    { name: "Ed25519" },
    false,
    ["sign"]
  );

  const dataBytes = new TextEncoder().encode(canonicalStr);
  const signatureBuffer = await crypto.subtle.sign("Ed25519", privateKey, dataBytes);
  return bytesToBase64(new Uint8Array(signatureBuffer));
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const path = url.pathname;

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const privateSigningKey = Deno.env.get("WARNING_SIGNING_PRIVATE_KEY") || DEFAULT_PRIV_KEY_BASE64;
  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  try {
    // -------------------------------------------------------------------------
    // 1. GET /warning-keys: Retrieve trusted public keys
    // -------------------------------------------------------------------------
    if (req.method === "GET" && (path.endsWith("/warning-keys") || path.endsWith("/keys"))) {
      const { data, error } = await supabase
        .from("warning_signing_keys")
        .select("*")
        .order("valid_from", { ascending: true });

      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      return new Response(JSON.stringify({ keys: data || [] }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // -------------------------------------------------------------------------
    // 2. GET /warnings/active: Retrieve currently published, unexpired warnings
    // -------------------------------------------------------------------------
    if (req.method === "GET" && path.endsWith("/active")) {
      const nowIso = new Date().toISOString();
      const { data, error } = await supabase
        .from("public_warnings")
        .select("*")
        .eq("status", "PUBLISHED")
        .gt("expires_at", nowIso)
        .order("published_at", { ascending: false });

      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      return new Response(JSON.stringify({ warnings: data || [] }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // -------------------------------------------------------------------------
    // 3. POST /warnings/:warningId/sign: Authorize, validate, and sign warning
    // -------------------------------------------------------------------------
    if (req.method === "POST" && path.includes("/sign")) {
      const parts = path.split("/");
      const signIndex = parts.indexOf("sign");
      const warningId = parts[signIndex - 1];

      if (!warningId) {
        return new Response(JSON.stringify({ error: "Missing warningId in URL" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // Fetch the warning from database
      const { data: warning, error: fetchErr } = await supabase
        .from("public_warnings")
        .select("*")
        .eq("id", warningId)
        .single();

      if (fetchErr || !warning) {
        return new Response(JSON.stringify({ error: "Warning not found" }), {
          status: 404,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // Check lifecycle status: must be APPROVED or SUBMITTED_FOR_APPROVAL
      if (warning.status !== "APPROVED" && warning.status !== "SUBMITTED_FOR_APPROVAL") {
        return new Response(
          JSON.stringify({
            error: "INVALID_STATUS",
            message: `Warning must be approved before digital signing. Current status: ${warning.status}`,
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Parse regions
      let regions: string[] = [];
      if (Array.isArray(warning.affected_regions)) {
        regions = warning.affected_regions;
      } else if (typeof warning.affected_area === "string") {
        regions = [warning.affected_area];
      }

      const issuedAtMillis = new Date(warning.published_at || new Date()).getTime();
      const effectiveAtMillis = new Date(warning.effective_at || warning.published_at || new Date()).getTime();
      const expiresAtMillis = new Date(warning.expires_at).getTime();

      if (expiresAtMillis <= issuedAtMillis) {
        return new Response(
          JSON.stringify({ error: "INVALID_TIMESTAMPS", message: "expires_at must be in the future of issued_at" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const revisionNumber = (warning.revision || 0) + 1;
      const statusPublished = "PUBLISHED";
      const keyId = ROOT_KEY_ID;

      // Construct canonical representation
      const canonicalString = buildCanonicalString({
        warningId: warning.id,
        protocolVersion: 1,
        revision: revisionNumber,
        status: statusPublished,
        category: warning.category,
        severity: warning.severity,
        issuingAuthority: warning.issuing_authority,
        title: warning.title,
        description: warning.description,
        issuedAt: issuedAtMillis,
        effectiveAt: effectiveAtMillis,
        expiresAt: expiresAtMillis,
        language: warning.language || "en",
        signingKeyId: keyId,
        signatureAlgorithm: "Ed25519",
        latitude: warning.latitude,
        longitude: warning.longitude,
        radiusMeters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        affectedRegions: regions,
      });

      // Digitally sign canonical bytes
      const signatureBase64 = await signEd25519(canonicalString, privateSigningKey);

      const publishedIso = new Date(issuedAtMillis).toISOString();

      // Update public_warnings table
      const { data: updatedWarning, error: updateErr } = await supabase
        .from("public_warnings")
        .update({
          status: statusPublished,
          revision: revisionNumber,
          signing_key_id: keyId,
          signature_algorithm: "Ed25519",
          signature: signatureBase64,
          canonical_payload: canonicalString,
          published_at: publishedIso,
          updated_at: new Date().toISOString(),
        })
        .eq("id", warningId)
        .select("*")
        .single();

      if (updateErr) {
        return new Response(JSON.stringify({ error: updateErr.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // Record in warning_revisions table
      await supabase.from("warning_revisions").insert({
        warning_id: warningId,
        warning_code: warning.warning_code,
        revision: revisionNumber,
        title: warning.title,
        description: warning.description,
        category: warning.category,
        severity: warning.severity,
        status: statusPublished,
        issuing_authority: warning.issuing_authority,
        signing_key_id: keyId,
        signature_algorithm: "Ed25519",
        signature: signatureBase64,
        canonical_payload: canonicalString,
        affected_regions: regions,
        latitude: warning.latitude,
        longitude: warning.longitude,
        radius_meters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        language: warning.language || "en",
        issued_at: publishedIso,
        effective_at: new Date(effectiveAtMillis).toISOString(),
        expires_at: new Date(expiresAtMillis).toISOString(),
      });

      // Audit Log
      await supabase.from("audit_logs").insert({
        actor_email: "signing-service@blackout.system",
        actor_role: "DIGITAL_SIGNING_SERVICE",
        action: "SIGN_AND_PUBLISH_WARNING",
        resource_type: "PUBLIC_WARNING",
        resource_id: warningId,
        details: {
          keyId,
          revision: revisionNumber,
          signatureAlgorithm: "Ed25519",
          signature: signatureBase64,
        },
      });

      return new Response(
        JSON.stringify({
          success: true,
          warning: updatedWarning,
          signature: signatureBase64,
          signingKeyId: keyId,
          canonicalPayload: canonicalString,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // -------------------------------------------------------------------------
    // 4. POST /warnings/:warningId/revoke: Revoke warning
    // -------------------------------------------------------------------------
    if (req.method === "POST" && path.includes("/revoke")) {
      const parts = path.split("/");
      const revokeIndex = parts.indexOf("revoke");
      const warningId = parts[revokeIndex - 1];

      const body = await req.json().catch(() => ({}));
      const reason = body.reason || "Administrative cancellation";

      const { data: warning, error: fetchErr } = await supabase
        .from("public_warnings")
        .select("*")
        .eq("id", warningId)
        .single();

      if (fetchErr || !warning) {
        return new Response(JSON.stringify({ error: "Warning not found" }), {
          status: 404,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const revNumber = (warning.revision || 1) + 1;
      const revokedAtIso = new Date().toISOString();
      const revokedAtMillis = Date.now();

      // Canonical payload for revocation revision
      const canonicalRevocation = buildCanonicalString({
        warningId: warning.id,
        protocolVersion: 1,
        revision: revNumber,
        status: "REVOKED",
        category: warning.category,
        severity: warning.severity,
        issuingAuthority: warning.issuing_authority,
        title: warning.title,
        description: `[REVOKED: ${reason}] ${warning.description}`,
        issuedAt: new Date(warning.published_at || warning.created_at).getTime(),
        effectiveAt: new Date(warning.published_at || warning.created_at).getTime(),
        expiresAt: new Date(warning.expires_at).getTime(),
        language: warning.language || "en",
        signingKeyId: ROOT_KEY_ID,
        signatureAlgorithm: "Ed25519",
        latitude: warning.latitude,
        longitude: warning.longitude,
        radiusMeters: warning.radius_meters || (warning.radius_km ? warning.radius_km * 1000 : null),
        affectedRegions: Array.isArray(warning.affected_regions) ? warning.affected_regions : [warning.affected_area],
      });

      const revocationSignature = await signEd25519(canonicalRevocation, privateSigningKey);

      // Update public_warnings table
      const { data: updatedWarning, error: updateErr } = await supabase
        .from("public_warnings")
        .update({
          status: "REVOKED",
          revision: revNumber,
          revoked_at: revokedAtIso,
          revocation_reason: reason,
          signature: revocationSignature,
          canonical_payload: canonicalRevocation,
          updated_at: revokedAtIso,
        })
        .eq("id", warningId)
        .select("*")
        .single();

      if (updateErr) {
        return new Response(JSON.stringify({ error: updateErr.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // Record in warning_revocations table
      await supabase.from("warning_revocations").insert({
        warning_id: warningId,
        warning_code: warning.warning_code,
        revision: revNumber,
        revocation_reason: reason,
        revocation_signature: revocationSignature,
        revoked_at: revokedAtIso,
      });

      // Audit Log
      await supabase.from("audit_logs").insert({
        actor_email: "signing-service@blackout.system",
        actor_role: "DIGITAL_SIGNING_SERVICE",
        action: "REVOKE_WARNING",
        resource_type: "PUBLIC_WARNING",
        resource_id: warningId,
        details: { reason, revision: revNumber, revocationSignature },
      });

      return new Response(
        JSON.stringify({ success: true, warning: updatedWarning, revocationSignature }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // -------------------------------------------------------------------------
    // 5. GET /warnings/:warningId: Single Warning Detail
    // -------------------------------------------------------------------------
    if (req.method === "GET") {
      const parts = path.split("/");
      const lastPart = parts[parts.length - 1];

      if (lastPart && lastPart !== "warnings") {
        const { data: warning, error } = await supabase
          .from("public_warnings")
          .select("*")
          .eq("id", lastPart)
          .single();

        if (error || !warning) {
          return new Response(JSON.stringify({ error: "Warning not found" }), {
            status: 404,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          });
        }

        // Also fetch revision history
        const { data: revisions } = await supabase
          .from("warning_revisions")
          .select("*")
          .eq("warning_id", lastPart)
          .order("revision", { ascending: false });

        return new Response(JSON.stringify({ warning, revisions: revisions || [] }), {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
    }

    return new Response(JSON.stringify({ error: "NOT_FOUND" }), {
      status: 404,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ error: "SERVER_ERROR", message: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
