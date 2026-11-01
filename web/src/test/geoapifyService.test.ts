import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { geoapifyService } from '../services/geoapifyService';

describe('GeoapifyService Location Enrichment & Search', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    geoapifyService.setApiKey('test_geoapify_key_12345');
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('1. Successfully reverse geocodes valid coordinates and marks address as approximate', async () => {
    const mockResponse = {
      features: [
        {
          properties: {
            formatted: 'Godwin Deluxe Hotel, Central Secretariat, Delhi, India',
            city: 'Delhi',
            district: 'South West Delhi',
            state: 'Delhi',
            country: 'India',
            postcode: '110001',
            rank: { confidence: 0.95 },
          },
        },
      ],
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    });

    const result = await geoapifyService.reverseGeocode(28.6139, 77.2090);

    expect(result).not.toBeNull();
    expect(result?.formatted).toBe('Godwin Deluxe Hotel, Central Secretariat, Delhi, India');
    expect(result?.city).toBe('Delhi');
    expect(result?.country).toBe('India');
    expect(result?.postalCode).toBe('110001');
    expect(result?.confidence).toBe(0.95);
    expect(result?.isApproximate).toBe(true);
    expect(result?.originalCoordinates).toEqual({ latitude: 28.6139, longitude: 77.2090 });
  });

  it('2. Throws error on invalid out-of-bounds coordinates', async () => {
    await expect(geoapifyService.reverseGeocode(95.0, 77.0)).rejects.toThrow(
      'Invalid GPS coordinates out of geographical range.'
    );

    await expect(geoapifyService.reverseGeocode(28.0, 195.0)).rejects.toThrow(
      'Invalid GPS coordinates out of geographical range.'
    );
  });

  it('3. Returns null for missing or null/zero coordinates without making API request', async () => {
    const fetchSpy = vi.fn();
    global.fetch = fetchSpy;

    const resNull = await geoapifyService.reverseGeocode(null, null);
    expect(resNull).toBeNull();

    const resZero = await geoapifyService.reverseGeocode(0, 0);
    expect(resZero).toBeNull();

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('4. Handles no geocoding results gracefully', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ features: [] }),
    });

    const res = await geoapifyService.reverseGeocode(10.0, 10.0);
    expect(res).toBeNull();
  });

  it('5. Handles HTTP 429 rate-limit response with specific error message', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 429,
    });

    await expect(geoapifyService.reverseGeocode(28.5, 77.5)).rejects.toThrow(
      'Geoapify rate limit exceeded. Please wait before retrying.'
    );
  });

  it('6. Handles request timeout via AbortError', async () => {
    const abortError = new Error('The user aborted a request.');
    abortError.name = 'AbortError';

    global.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(geoapifyService.reverseGeocode(28.5, 77.5)).rejects.toThrow(
      'Reverse geocode request timed out.'
    );
  });

  it('7. Returns cached result on subsequent calls for same coordinates', async () => {
    const mockResponse = {
      features: [
        {
          properties: {
            formatted: 'Connaught Place, New Delhi',
            city: 'New Delhi',
          },
        },
      ],
    };

    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    });
    global.fetch = fetchSpy;

    const res1 = await geoapifyService.reverseGeocode(28.6315, 77.2167);
    const res2 = await geoapifyService.reverseGeocode(28.6315, 77.2167);

    expect(res1?.formatted).toBe('Connaught Place, New Delhi');
    expect(res2?.formatted).toBe('Connaught Place, New Delhi');
    expect(fetchSpy).toHaveBeenCalledTimes(1); // Cached on second call!
  });

  it('8. Throws error on invalid or restricted API credentials (401/403)', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
    });

    await expect(geoapifyService.reverseGeocode(28.1, 77.1)).rejects.toThrow(
      'Invalid or restricted Geoapify API credentials.'
    );
  });

  it('9. Forward geocoding search returns mapped place coordinates', async () => {
    const mockSearchResults = {
      features: [
        {
          properties: {
            formatted: 'Connaught Place, New Delhi, DL, India',
            city: 'New Delhi',
            country: 'India',
          },
          geometry: {
            coordinates: [77.2194, 28.6318],
          },
        },
      ],
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockSearchResults,
    });

    const results = await geoapifyService.searchPlaces('Connaught Place');
    expect(results).toHaveLength(1);
    expect(results[0].formatted).toBe('Connaught Place, New Delhi, DL, India');
    expect(results[0].latitude).toBe(28.6318);
    expect(results[0].longitude).toBe(77.2194);
  });

  it('10. Provides tactical dark map style URL when key is set, and null when key is cleared', () => {
    expect(geoapifyService.getTacticalMapStyleUrl()).toContain(
      'https://maps.geoapify.com/v1/styles/dark-matter-dark-purple/style.json?apiKey=test_geoapify_key_12345'
    );

    geoapifyService.setApiKey(null);
    expect(geoapifyService.getTacticalMapStyleUrl()).toBeNull();
  });
});

