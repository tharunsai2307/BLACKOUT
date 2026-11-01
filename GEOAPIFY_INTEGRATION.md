# BLACKOUT — Geoapify Location Services Integration Specification

## 1. Executive Summary & Purpose

In disaster response and emergency coordination, raw decimal coordinates (e.g. `28.6139, 77.2090`) can cause cognitive friction for control room operators who need immediate spatial recognition. **Geoapify Location Services** provides readable location enrichment (reverse geocoding), place and landmark search (forward geocoding), and tactical dark map styling for the **BLACKOUT Operations Portal**.

### Core Tenets:
1. **Pristine GPS Coordinates Inviolability**: Reverse-geocoded addresses are strictly decorative metadata. The underlying hardware GPS coordinates, accuracy radius, and fix timestamps captured by the offline Android node are **never modified, rounded, or overwritten**.
2. **Explicit Approximation Badging**: Because satellite fixes in disaster zones have variable precision ($\pm 5\text{m}$ to $\pm 100\text{m}$), all geocoded addresses are explicitly rendered with an `APPROXIMATE` badge in the UI and database.
3. **Resilient Fallback**: If the Geoapify API key is missing, network is disconnected, or rate limits are reached, the system falls back gracefully to OpenStreetMap raster tiles without interrupting incident triage.

---

## 2. Capability Architecture

```
+-------------------------------------------------------------+
|                Civilian Android SOS Beacon                  |
|  - Direct Satellite GNSS Fix: lat=28.6139, lon=77.2090       |
|  - Accuracy: ±8.5m, Epoch Timestamp                         |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|               Supabase Database (emergency_alerts)          |
|  - latitude: 28.6139 (Preserved permanently)                |
|  - longitude: 77.2090 (Preserved permanently)               |
|  - geocoded_address: "Central Secretariat, Delhi, India"    |
|  - geocoded_is_approximate: TRUE                            |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|           Operations Portal & MapLibre Dashboard            |
|  - Tactical Dark Style: dark-matter-dark-purple             |
|  - Forward Search: "Connaught Place" -> Target Marker      |
|  - Side-by-side display of Address & Raw GPS Fix             |
+-------------------------------------------------------------+
```

---

## 3. Implementation Details

### 3.1 Reverse Geocoding Service (`web/src/services/geoapifyService.ts`)
- **Endpoint**: `https://api.geoapify.com/v1/geocode/reverse`
- **Parameters**: `lat`, `lon`, `apiKey`
- **Output Schema**:
  ```typescript
  export interface GeocodedAddress {
    formatted: string;
    locality?: string | null;
    city?: string | null;
    district?: string | null;
    state?: string | null;
    country?: string | null;
    postalCode?: string | null;
    confidence: number;
    isApproximate: boolean; // Always true
    originalCoordinates: {
      latitude: number;
      longitude: number;
    };
  }
  ```
- **Caching**: In-memory LRU cache with spatial rounding to 4 decimal places (~11 meters) and 1-hour TTL. Prevents redundant API consumption for alerts within the same tactical incident perimeter.
- **Timeouts**: 6-second `AbortController` timeout prevents UI blocking during backhaul degradation.

### 3.2 Forward Geocoding & Map Positioning
- **Endpoint**: `https://api.geoapify.com/v1/geocode/search`
- **Usage**: Operators can type an address or landmark in the floating search bar on the Tactical Map (`EmergencyMap.tsx`).
- **Interaction**: The map flies smoothly to the target coordinate, displays a pulsating cyan target marker (`⌖`), and displays matching coordinates.

### 3.3 Tactical Dark Map Tile Integration
- **Style URL**: `https://maps.geoapify.com/v1/styles/dark-matter-dark-purple/style.json?apiKey=${VITE_GEOAPIFY_API_KEY}`
- **Fallback**: If the API key is not supplied or if MapLibre triggers an `error` event, the map instantly falls back to OpenStreetMap free raster tiles (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`).

---

## 4. Security, Credentials & Quota Management

### 4.1 Credential Protection Policy
- **Never Hardcode Secrets**: The previously shared Geoapify key is considered publicly exposed. Production deployments must use a newly generated, origin-restricted key.
- **Environment Variable Configuration**:
  - Web Portal: `VITE_GEOAPIFY_API_KEY` (configured in `.env.local` / hosting build variables).
  - Backend Edge Functions: `GEOAPIFY_API_KEY` (injected via Supabase Edge Function Secrets).
- **HTTP Referrer Restrictions**: In the Geoapify Project Console, restrict the browser key to authorized domain origins (e.g. `https://blackout.emergency.network/*`).

### 4.2 Rate Limits & Free-Tier Quotas
- **Free Plan Allowance**: 3,000 requests/day, max 5 requests/second.
- **Rate Limit Handling (HTTP 429)**: The client catches HTTP 429 and throws a friendly notification (`Geoapify rate limit exceeded. Please wait before retrying.`) while continuing to display the pristine raw GPS coordinates.
- **Log Masking**: API keys are stripped from all browser `console` logs and backend Edge Function logs.

