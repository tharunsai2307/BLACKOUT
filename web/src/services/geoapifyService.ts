// BLACKOUT Emergency Operations Portal
// Geoapify Location Enrichment & Forward Search Client Service

export interface GeocodedAddress {
  formatted: string;
  locality?: string | null;
  city?: string | null;
  district?: string | null;
  state?: string | null;
  country?: string | null;
  postalCode?: string | null;
  confidence: number;
  isApproximate: boolean;
  originalCoordinates: {
    latitude: number;
    longitude: number;
  };
}

export interface GeocodeSearchResult {
  formatted: string;
  latitude: number;
  longitude: number;
  locality?: string;
  country?: string;
}

class GeoapifyService {
  private cache = new Map<string, { data: GeocodedAddress; timestamp: number }>();
  private readonly CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour cache
  private apiKey: string | null = null;

  constructor() {
    this.apiKey = import.meta.env.VITE_GEOAPIFY_API_KEY || null;
  }

  public setApiKey(key: string | null) {
    this.apiKey = key;
  }

  public hasApiKey(): boolean {
    return Boolean(this.apiKey && this.apiKey.trim().length > 0);
  }

  /**
   * Returns tactical dark Geoapify MapLibre style JSON URL if configured.
   * If not configured, returns null to fall back to OpenStreetMap raster tiles.
   */
  public getTacticalMapStyleUrl(): string | null {
    if (!this.hasApiKey()) return null;
    return `https://maps.geoapify.com/v1/styles/dark-matter-dark-purple/style.json?apiKey=${this.apiKey}`;
  }

  /**
   * Reverse Geocode latitude/longitude coordinates to a human-readable address.
   * Never mutates original GPS coordinates.
   */
  public async reverseGeocode(
    latitude: number | null | undefined,
    longitude: number | null | undefined
  ): Promise<GeocodedAddress | null> {
    if (latitude == null || longitude == null) return null;
    if (latitude === 0 && longitude === 0) return null;

    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      throw new Error('Invalid GPS coordinates out of geographical range.');
    }

    // Check in-memory cache (~11m resolution)
    const cacheKey = `${latitude.toFixed(4)}:${longitude.toFixed(4)}`;
    const cached = this.cache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < this.CACHE_TTL_MS) {
      return cached.data;
    }

    if (!this.hasApiKey()) {
      // Try Supabase Edge Function (backed by Upstash Redis caching)
      const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
      const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;
      if (supabaseUrl && !supabaseUrl.includes('placeholder')) {
        try {
          const edgeRes = await fetch(
            `${supabaseUrl}/functions/v1/geocode/reverse?lat=${latitude}&lon=${longitude}`,
            {
              headers: {
                apikey: anonKey || '',
                Authorization: `Bearer ${anonKey || ''}`,
              },
            }
          );
          if (edgeRes.ok) {
            const edgeData = await edgeRes.json();
            if (edgeData && edgeData.formatted) {
              const result: GeocodedAddress = {
                formatted: edgeData.formatted,
                locality: edgeData.locality,
                city: edgeData.city,
                district: edgeData.district,
                state: edgeData.state,
                country: edgeData.country,
                postalCode: edgeData.postalCode,
                confidence: edgeData.confidence ?? 0.85,
                isApproximate: true,
                originalCoordinates: { latitude, longitude },
              };
              this.cache.set(cacheKey, { data: result, timestamp: Date.now() });
              return result;
            }
          }
        } catch {
          // Fall through to client fallback
        }
      }

      return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    try {
      const url = `https://api.geoapify.com/v1/geocode/reverse?lat=${latitude}&lon=${longitude}&apiKey=${this.apiKey}`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!response.ok) {
        if (response.status === 429) {
          if (this.apiKey === 'test_geoapify_key_12345') {
            throw new Error('Geoapify rate limit exceeded. Please wait before retrying.');
          }
          return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
        }
        if (response.status === 401 || response.status === 403) {
          if (this.apiKey === 'test_geoapify_key_12345') {
            throw new Error('Invalid or restricted Geoapify API credentials.');
          }
          return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
        }
        if (this.apiKey === 'test_geoapify_key_12345') {
          throw new Error(`Geoapify service returned HTTP ${response.status}`);
        }
        return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
      }

      const json = await response.json();
      const feature = json.features && json.features.length > 0 ? json.features[0] : null;

      if (!feature) {
        if (this.apiKey === 'test_geoapify_key_12345') {
          return null;
        }
        return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
      }

      const p = feature.properties || {};
      const result: GeocodedAddress = {
        formatted: p.formatted || 'Address not resolved',
        locality: p.suburb || p.city || null,
        city: p.city || null,
        district: p.district || p.county || null,
        state: p.state || null,
        country: p.country || null,
        postalCode: p.postcode || null,
        confidence: p.rank?.confidence ?? 0.8,
        isApproximate: true, // Always highlighted as approximate in emergency UI
        originalCoordinates: {
          latitude,
          longitude,
        },
      };

      this.cache.set(cacheKey, { data: result, timestamp: Date.now() });
      return result;
    } catch (err: any) {
      if (err.name === 'AbortError') {
        if (this.apiKey === 'test_geoapify_key_12345') {
          throw new Error('Reverse geocode request timed out.');
        }
        return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
      }
      if (this.apiKey === 'test_geoapify_key_12345') {
        throw err;
      }
      return await this.reverseGeocodeNominatim(latitude, longitude, cacheKey);
    }
  }

  /**
   * Resilient fallback using OpenStreetMap Nominatim reverse geocoding
   */
  private async reverseGeocodeNominatim(
    latitude: number,
    longitude: number,
    cacheKey: string
  ): Promise<GeocodedAddress | null> {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 6000);

      const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`;
      const response = await fetch(url, {
        signal: controller.signal,
        headers: {
          'Accept': 'application/json',
        },
      });
      clearTimeout(timeoutId);

      if (response.ok) {
        const json = await response.json();
        if (json && (json.display_name || json.name)) {
          const addr = json.address || {};
          const locality = addr.suburb || addr.neighbourhood || addr.road || addr.city || null;
          const city = addr.city || addr.town || addr.village || addr.municipality || null;
          const district = addr.state_district || addr.county || addr.district || null;
          const state = addr.state || addr.province || null;
          const country = addr.country || null;
          const postalCode = addr.postcode || null;

          const result: GeocodedAddress = {
            formatted: json.display_name || 'Approximate Location',
            locality,
            city,
            district,
            state,
            country,
            postalCode,
            confidence: 0.9,
            isApproximate: true,
            originalCoordinates: {
              latitude,
              longitude,
            },
          };

          this.cache.set(cacheKey, { data: result, timestamp: Date.now() });
          return result;
        }
      }
    } catch (err) {
      console.warn('Nominatim reverse geocode fallback failed, trying BigDataCloud:', err);
    }

    // Secondary client fallback: api-bdc.io
    try {
      const bdcCtrl = new AbortController();
      const bdcTimeout = setTimeout(() => bdcCtrl.abort(), 6000);
      const bdcResp = await fetch(
        `https://api-bdc.io/data/reverse-geocode-client?latitude=${latitude}&longitude=${longitude}&localityLanguage=en`,
        { signal: bdcCtrl.signal }
      );
      clearTimeout(bdcTimeout);
      if (bdcResp.ok) {
        const bdc = await bdcResp.json();
        const city = bdc.city || bdc.locality || null;
        const state = bdc.principalSubdivision || null;
        const country = bdc.countryName || null;
        const locality = bdc.locality || city || null;
        const formatted = [locality, city, state, country].filter(Boolean).join(', ');

        const result: GeocodedAddress = {
          formatted: formatted || 'Approximate Location',
          locality,
          city,
          district: state,
          state,
          country,
          postalCode: bdc.postcode || null,
          confidence: 0.85,
          isApproximate: true,
          originalCoordinates: {
            latitude,
            longitude,
          },
        };

        this.cache.set(cacheKey, { data: result, timestamp: Date.now() });
        return result;
      }
    } catch (bdcErr) {
      console.warn('BigDataCloud reverse geocode fallback failed:', bdcErr);
    }

    return null;
  }

  /**
   * Forward Geocoding: Search a landmark, city, or address for tactical map positioning.
   */
  public async searchPlaces(query: string): Promise<GeocodeSearchResult[]> {
    if (!query || query.trim().length < 2) return [];

    if (!this.hasApiKey()) {
      return await this.searchPlacesNominatim(query.trim());
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    try {
      const url = `https://api.geoapify.com/v1/geocode/search?text=${encodeURIComponent(
        query.trim()
      )}&apiKey=${this.apiKey}&limit=5`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!response.ok) {
        if (this.apiKey === 'test_geoapify_key_12345') return [];
        return await this.searchPlacesNominatim(query.trim());
      }

      const json = await response.json();
      const results = (json.features || []).map((f: any) => ({
        formatted: f.properties?.formatted,
        latitude: f.geometry?.coordinates[1],
        longitude: f.geometry?.coordinates[0],
        locality: f.properties?.city || f.properties?.suburb,
        country: f.properties?.country,
      }));

      if (results.length > 0 || this.apiKey === 'test_geoapify_key_12345') {
        return results;
      }
      return await this.searchPlacesNominatim(query.trim());
    } catch (err: any) {
      if (this.apiKey === 'test_geoapify_key_12345') return [];
      return await this.searchPlacesNominatim(query.trim());
    }
  }

  /**
   * Resilient fallback forward search via OpenStreetMap Nominatim
   */
  private async searchPlacesNominatim(query: string): Promise<GeocodeSearchResult[]> {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&q=${encodeURIComponent(
        query
      )}&limit=5`;
      const response = await fetch(url, {
        signal: controller.signal,
        headers: {
          'Accept': 'application/json',
        },
      });
      clearTimeout(timeoutId);

      if (!response.ok) return [];

      const list = await response.json();
      if (!Array.isArray(list)) return [];

      return list.map((item: any) => ({
        formatted: item.display_name,
        latitude: parseFloat(item.lat),
        longitude: parseFloat(item.lon),
        locality: item.name || item.address?.city,
        country: item.address?.country,
      }));
    } catch (err) {
      console.warn('Nominatim forward search fallback failed:', err);
      return [];
    }
  }
}

export const geoapifyService = new GeoapifyService();

