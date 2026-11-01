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
      return null;
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    try {
      const url = `https://api.geoapify.com/v1/geocode/reverse?lat=${latitude}&lon=${longitude}&apiKey=${this.apiKey}`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!response.ok) {
        if (response.status === 429) {
          throw new Error('Geoapify rate limit exceeded. Please wait before retrying.');
        }
        if (response.status === 401 || response.status === 403) {
          throw new Error('Invalid or restricted Geoapify API credentials.');
        }
        throw new Error(`Geoapify service returned HTTP ${response.status}`);
      }

      const json = await response.json();
      const feature = json.features && json.features.length > 0 ? json.features[0] : null;

      if (!feature) {
        return null;
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
        throw new Error('Reverse geocode request timed out.');
      }
      throw err;
    }
  }

  /**
   * Forward Geocoding: Search a landmark, city, or address for tactical map positioning.
   */
  public async searchPlaces(query: string): Promise<GeocodeSearchResult[]> {
    if (!query || query.trim().length < 2) return [];
    if (!this.hasApiKey()) return [];

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    try {
      const url = `https://api.geoapify.com/v1/geocode/search?text=${encodeURIComponent(
        query.trim()
      )}&apiKey=${this.apiKey}&limit=5`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!response.ok) {
        return [];
      }

      const json = await response.json();
      return (json.features || []).map((f: any) => ({
        formatted: f.properties?.formatted,
        latitude: f.geometry?.coordinates[1],
        longitude: f.geometry?.coordinates[0],
        locality: f.properties?.city || f.properties?.suburb,
        country: f.properties?.country,
      }));
    } catch (err: any) {
      return [];
    }
  }
}

export const geoapifyService = new GeoapifyService();
