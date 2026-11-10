// BLACKOUT Decentralized Emergency Communications Platform
// Supabase Edge Function: /geocode
// Server-Side Geoapify Location Enrichment & Forward Search Gateway
// Upstash Redis Features:
// 1. IP-based Rate Limiting (60 requests / minute) with Retry-After header
// 2. Persistent Geocoding Result Caching (24-hour TTL) across serverless instances
// 3. Fallback to in-memory cache and direct API if Redis is unreachable

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { redis } from "../_shared/redis.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

// In-Memory Geocoding Fallback Cache (used if Redis is offline)
const inMemoryCache = new Map<string, { data: any; expiresAt: number }>();
const IN_MEMORY_TTL_MS = 60 * 60 * 1000; // 1 hour

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const path = url.pathname;
  const clientIp = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown-ip";

  // --------------------------------------------------------------------------
  // 1. Redis-Backed Rate Limiting (60 requests per minute per IP)
  // --------------------------------------------------------------------------
  const rateLimit = await redis.checkRateLimit("geocode", clientIp, 60, 60);
  if (!rateLimit.allowed) {
    return new Response(
      JSON.stringify({
        error: "RATE_LIMIT_EXCEEDED",
        message: "Geocoding and map search rate limit exceeded. Please retry later.",
        retryAfter: rateLimit.retryAfterSeconds,
      }),
      {
        status: 429,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
          "Retry-After": String(rateLimit.retryAfterSeconds),
          "X-RateLimit-Limit": String(rateLimit.limit),
          "X-RateLimit-Remaining": String(rateLimit.remaining),
          "X-RateLimit-Reset": String(rateLimit.reset),
        },
      }
    );
  }

  const apiKey = Deno.env.get("GEOAPIFY_API_KEY");

  // Helper: OpenStreetMap Nominatim reverse geocode fallback
  const fetchNominatimReverse = async (lat: number, lon: number) => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    try {
      const resp = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}`,
        {
          signal: controller.signal,
          headers: { "User-Agent": "BLACKOUT-Emergency-Response-Platform/1.0" },
        }
      );
      clearTimeout(timeoutId);
      if (resp.ok) {
        const json = await resp.json();
        if (json && (json.display_name || json.name)) {
          const addr = json.address || {};
          return {
            formatted: json.display_name || null,
            locality: addr.suburb || addr.neighbourhood || addr.road || addr.city || null,
            city: addr.city || addr.town || addr.village || null,
            district: addr.state_district || addr.county || addr.district || null,
            state: addr.state || null,
            country: addr.country || null,
            postalCode: addr.postcode || null,
            confidence: 0.9,
            isApproximate: true,
            originalCoordinates: { latitude: lat, longitude: lon },
          };
        }
      }
    } catch {
      // Fall through to BigDataCloud
    }

    // Secondary server-side fallback: api-bdc.io
    try {
      const bdcCtrl = new AbortController();
      const bdcTimeout = setTimeout(() => bdcCtrl.abort(), 6000);
      const bdcResp = await fetch(
        `https://api-bdc.io/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`,
        { signal: bdcCtrl.signal }
      );
      clearTimeout(bdcTimeout);
      if (bdcResp.ok) {
        const bdc = await bdcResp.json();
        const city = bdc.city || bdc.locality || null;
        const state = bdc.principalSubdivision || null;
        const country = bdc.countryName || null;
        const locality = bdc.locality || city || null;
        const formatted = [locality, city, state, country].filter(Boolean).join(", ");

        return {
          formatted: formatted || "Approximate Coordinates",
          locality,
          city,
          district: state,
          state,
          country,
          postalCode: bdc.postcode || null,
          confidence: 0.85,
          isApproximate: true,
          originalCoordinates: { latitude: lat, longitude: lon },
        };
      }
    } catch {
      // return null if both fail
    }

    return null;
  };

  // Helper: OpenStreetMap Nominatim forward search fallback
  const fetchNominatimSearch = async (query: string) => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    try {
      const resp = await fetch(
        `https://nominatim.openstreetmap.org/search?format=jsonv2&q=${encodeURIComponent(query)}&limit=5`,
        {
          signal: controller.signal,
          headers: { "User-Agent": "BLACKOUT-Emergency-Response-Platform/1.0" },
        }
      );
      clearTimeout(timeoutId);
      if (!resp.ok) return [];
      const list = await resp.json();
      if (!Array.isArray(list)) return [];
      return list.map((item: any) => ({
        formatted: item.display_name,
        latitude: parseFloat(item.lat),
        longitude: parseFloat(item.lon),
        locality: item.name || item.address?.city,
        country: item.address?.country,
      }));
    } catch {
      return [];
    }
  };

  // --------------------------------------------------------------------------
  // 2. Reverse Geocoding: /geocode/reverse?lat=...&lon=...
  // --------------------------------------------------------------------------
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

    // Cache key rounded to ~11 metres (4 decimal places)
    const redisCacheKey = `geo:rev:${lat.toFixed(4)}:${lon.toFixed(4)}`;

    // 2.1 Check Upstash Redis Cache first
    const cachedRedis = await redis.get<any>(redisCacheKey);
    if (cachedRedis) {
      return new Response(JSON.stringify({ ...cachedRedis, cached: true, source: "upstash-redis" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "HIT" },
      });
    }

    // 2.2 Check in-memory fallback cache
    const memCached = inMemoryCache.get(redisCacheKey);
    if (memCached && memCached.expiresAt > Date.now()) {
      return new Response(JSON.stringify({ ...memCached.data, cached: true, source: "in-memory-fallback" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "HIT" },
      });
    }

    try {
      let result: any = null;
      let sourceName = "geoapify";

      if (apiKey) {
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 4000);

          const targetUrl = `https://api.geoapify.com/v1/geocode/reverse?lat=${lat}&lon=${lon}&apiKey=${apiKey}`;
          const resp = await fetch(targetUrl, { signal: controller.signal });
          clearTimeout(timeoutId);

          if (resp.ok) {
            const raw = await resp.json();
            const feature = raw.features && raw.features.length > 0 ? raw.features[0] : null;
            if (feature) {
              const p = feature.properties || {};
              result = {
                formatted: p.formatted || null,
                locality: p.suburb || p.city || null,
                city: p.city || null,
                district: p.district || p.county || null,
                state: p.state || null,
                country: p.country || null,
                postalCode: p.postcode || null,
                confidence: p.rank?.confidence || 0.8,
                isApproximate: true,
                originalCoordinates: { latitude: lat, longitude: lon },
              };
            }
          }
        } catch {
          // Fall through to Nominatim
        }
      }

      if (!result) {
        result = await fetchNominatimReverse(lat, lon);
        sourceName = "osm-nominatim";
      }

      if (!result) {
        const noAddressResult = {
          formatted: null,
          isApproximate: true,
          message: "No address found for these coordinates",
          originalCoordinates: { latitude: lat, longitude: lon },
        };
        await redis.set(redisCacheKey, noAddressResult, { ex: 3600 });
        return new Response(JSON.stringify(noAddressResult), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // Store in Redis with 24-hour TTL (86400s)
      await redis.set(redisCacheKey, result, { ex: 86400 });

      // Store in memory fallback as well
      if (inMemoryCache.size > 200) {
        const firstKey = inMemoryCache.keys().next().value;
        if (firstKey) inMemoryCache.delete(firstKey);
      }
      inMemoryCache.set(redisCacheKey, { data: result, expiresAt: Date.now() + IN_MEMORY_TTL_MS });

      return new Response(JSON.stringify({ ...result, cached: false, source: sourceName }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "MISS" },
      });
    } catch (err: any) {
      return new Response(
        JSON.stringify({
          error: "Reverse geocoding unavailable",
          code: "GEOCODE_ERROR",
        }),
        {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }
  }

  // --------------------------------------------------------------------------
  // 3. Forward Geocoding: /geocode/search?text=...
  // --------------------------------------------------------------------------
  if (path.endsWith("/search") || url.searchParams.has("text")) {
    const query = url.searchParams.get("text")?.trim();
    if (!query || query.length === 0) {
      return new Response(JSON.stringify({ error: "Missing search text query parameter" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const redisSearchKey = `geo:search:${encodeURIComponent(query.toLowerCase())}`;

    // 3.1 Check Redis search cache (24h TTL)
    const cachedSearch = await redis.get<any>(redisSearchKey);
    if (cachedSearch) {
      return new Response(JSON.stringify({ results: cachedSearch, cached: true, source: "upstash-redis" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "HIT" },
      });
    }

    try {
      let results: any[] = [];
      let sourceName = "geoapify";

      if (apiKey) {
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 4000);

          const targetUrl = `https://api.geoapify.com/v1/geocode/search?text=${encodeURIComponent(query)}&apiKey=${apiKey}&limit=5`;
          const resp = await fetch(targetUrl, { signal: controller.signal });
          clearTimeout(timeoutId);

          if (resp.ok) {
            const raw = await resp.json();
            results = (raw.features || []).map((f: any) => ({
              formatted: f.properties?.formatted,
              latitude: f.geometry?.coordinates[1],
              longitude: f.geometry?.coordinates[0],
              locality: f.properties?.city || f.properties?.suburb,
              country: f.properties?.country,
            }));
          }
        } catch {
          // Fall through to Nominatim search
        }
      }

      if (results.length === 0) {
        results = await fetchNominatimSearch(query);
        sourceName = "osm-nominatim";
      }

      // Store in Redis (24h TTL)
      await redis.set(redisSearchKey, results, { ex: 86400 });

      return new Response(JSON.stringify({ results, cached: false, source: sourceName }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "X-Cache": "MISS" },
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
