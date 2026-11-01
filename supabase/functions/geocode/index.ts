// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /geocode
// Server-Side Geoapify Location Enrichment & Forward Search Gateway

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

// In-Memory Geocoding LRU Cache (Up to 200 spatial points)
const cache = new Map<string, { data: any; expiresAt: number }>();
const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour cache for reverse geocode coordinates

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const path = url.pathname;
  const apiKey = Deno.env.get("GEOAPIFY_API_KEY");

  if (!apiKey) {
    return new Response(JSON.stringify({
      error: "Geoapify API key not configured on server",
      code: "GEOAPIFY_KEY_MISSING",
    }), {
      status: 503,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  // 1. Reverse Geocoding: /geocode/reverse?lat=...&lon=...
  if (path.endsWith("/reverse") || url.searchParams.has("lat")) {
    const latStr = url.searchParams.get("lat");
    const lonStr = url.searchParams.get("lon");

    if (!latStr || !lonStr) {
      return new Response(JSON.stringify({ error: "Missing required query parameters: lat, lon" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const lat = parseFloat(latStr);
    const lon = parseFloat(lonStr);

    if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      return new Response(JSON.stringify({ error: "Invalid geographic coordinates." }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Cache key rounded to ~11 meters (4 decimal places)
    const cacheKey = `rev:${lat.toFixed(4)}:${lon.toFixed(4)}`;
    const cached = cache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      return new Response(JSON.stringify({ ...cached.data, cached: true }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const targetUrl = `https://api.geoapify.com/v1/geocode/reverse?lat=${lat}&lon=${lon}&apiKey=${apiKey}`;
      const resp = await fetch(targetUrl, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!resp.ok) {
        if (resp.status === 429) {
          return new Response(JSON.stringify({ error: "Geoapify API rate limit exceeded.", code: "RATE_LIMITED" }), {
            status: 429,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          });
        }
        return new Response(JSON.stringify({ error: `Upstream error: HTTP ${resp.status}`, code: "UPSTREAM_ERROR" }), {
          status: resp.status,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const raw = await resp.json();
      const feature = raw.features && raw.features.length > 0 ? raw.features[0] : null;

      if (!feature) {
        return new Response(JSON.stringify({
          formatted: null,
          isApproximate: true,
          message: "No address found for these coordinates",
        }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const p = feature.properties || {};
      const result = {
        formatted: p.formatted || null,
        locality: p.suburb || p.city || null,
        city: p.city || null,
        district: p.district || p.county || null,
        state: p.state || null,
        country: p.country || null,
        postalCode: p.postcode || null,
        confidence: p.rank?.confidence || 0.8,
        isApproximate: true, // Always marked approximate for emergency triage
        originalCoordinates: { latitude: lat, longitude: lon },
      };

      // Store in LRU cache
      if (cache.size > 200) {
        const firstKey = cache.keys().next().value;
        if (firstKey) cache.delete(firstKey);
      }
      cache.set(cacheKey, { data: result, expiresAt: Date.now() + CACHE_TTL_MS });

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    } catch (err: any) {
      return new Response(JSON.stringify({
        error: err.name === "AbortError" ? "Reverse geocode request timed out" : "Failed to reach Geoapify service",
        code: "TIMEOUT_OR_NETWORK",
      }), {
        status: 504,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
  }

  // 2. Forward Geocoding: /geocode/search?text=...
  if (path.endsWith("/search") || url.searchParams.has("text")) {
    const query = url.searchParams.get("text");
    if (!query || query.trim().length === 0) {
      return new Response(JSON.stringify({ error: "Missing search text query parameter" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const targetUrl = `https://api.geoapify.com/v1/geocode/search?text=${encodeURIComponent(query)}&apiKey=${apiKey}&limit=5`;
      const resp = await fetch(targetUrl, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!resp.ok) {
        return new Response(JSON.stringify({ error: `Upstream error: HTTP ${resp.status}` }), {
          status: resp.status,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const raw = await resp.json();
      const results = (raw.features || []).map((f: any) => ({
        formatted: f.properties?.formatted,
        latitude: f.geometry?.coordinates[1],
        longitude: f.geometry?.coordinates[0],
        locality: f.properties?.city || f.properties?.suburb,
        country: f.properties?.country,
      }));

      return new Response(JSON.stringify({ results }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    } catch (err: any) {
      return new Response(JSON.stringify({ error: "Search query failed or timed out" }), {
        status: 504,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
  }

  return new Response(JSON.stringify({ error: "Invalid geocode endpoint. Use /reverse or /search" }), {
    status: 404,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});

