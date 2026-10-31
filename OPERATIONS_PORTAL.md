# BLACKOUT — Government Operations Portal (Phase 6)

The **BLACKOUT Operations Portal** is an authoritative, dark-themed, military/tactical web application built for government disaster management authorities (NDMA/SDMA), control room operators, and emergency first responders.

It connects directly to the central **Supabase PostgreSQL database** and mesh gateway ingestion infrastructure established in Phase 5, providing real-time situational awareness, spatial telemetry mapping, incident triage and verification, emergency response team dispatching, civil defense broadcast warnings, and tamper-evident audit ledgers.

---

## 1. System Architecture & Tech Stack

```
+-------------------------------------------------------------+
|               BLACKOUT Mesh Network (Phase 1-4)             |
|   (Android Java/Room Offline Store-Carry-Forward Nodes)     |
+-------------------------------------------------------------+
                              |
                              v  (HTTP POST /functions/v1/sos_ingest)
+-------------------------------------------------------------+
|          Phase 5 Central Emergency Cloud Infrastructure      |
|  - Supabase PostgreSQL (emergency_alerts, gateway_upload)  |
|  - Realtime WebSocket Engine (Postgres Changes CDC)         |
+-------------------------------------------------------------+
                              |
                              v  (Realtime WebSockets + Supabase JS SDK)
+-------------------------------------------------------------+
|          Phase 6 Government Operations Web Portal           |
|  - React 18 & TypeScript (Vite Bundle)                     |
|  - Tailwind CSS (Custom Dark Tactical Palette)             |
|  - MapLibre GL JS + Free OpenStreetMap Raster Tiles        |
|  - Recharts (Real Aggregated Mathematical Distributions)    |
|  - Database-backed RBAC with Row-Level Security (RLS)      |
+-------------------------------------------------------------+
```

### Technology Matrix
- **Framework**: React 18, TypeScript, Vite
- **Styling**: Tailwind CSS with custom `operations-*` tactical palette
- **Mapping**: MapLibre GL JS with OpenStreetMap raster tiles (free, tokenless, completely self-hosted friendly)
- **Database Client**: `@supabase/supabase-js` v2.39+
- **Live Ingestion**: Supabase Realtime Channels (PostgreSQL Change-Data-Capture)
- **Visual Analytics**: Recharts
- **Icons**: Lucide React
- **Testing**: Vitest & React Testing Library

---

## 2. Role-Based Access Control (RBAC) & Security

The portal enforces strict Zero-Trust role-based access control backed by database Row-Level Security (RLS) in `supabase/migrations/20261101010000_phase6_operations_portal.sql`.

| Role | Incident Viewing | Incident Verification | Team Dispatch | Public Warning Broadcast | Audit Trail Access |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`SUPER_ADMIN`** | Yes (Full) | Yes (Full) | Yes (Full) | Yes (Create, Approve, Publish, Revoke) | Yes (Full) |
| **`CONTROL_ROOM_OPERATOR`** | Yes (Full) | Yes (Full) | Yes (Full) | Draft & Submit Only | Yes (View) |
| **`FIELD_RESPONSE_COORDINATOR`** | Yes (View) | No | Yes (Update Status) | No | No |
| **`PUBLIC_WARNING_PUBLISHER`** | Yes (View) | No | No | Yes (Approve, Publish, Revoke) | No |
| **`VIEWER`** | Yes (View) | No | No | No | No |

---

## 3. Core Operational Modules

### 3.1. Main Dashboard (`/dashboard`)
- **Key Performance Indicators**: Real-time counts of total ingested alerts, pending review queue, active critical threats, and ready response units.
- **Live Alert Feed**: Real-time stream listening to incoming SOS payloads uploaded by gateway Android devices.
- **Realtime Connection Indicator**: Only displays `LIVE REALTIME STREAM` when the WebSocket connection is physically open and subscribed. Shows `SYNCHRONIZING FEED...` or `OFFLINE (MANUAL REFRESH)` otherwise.
- **Threat Breakdown**: Real categorical distribution computed from the database.

### 3.2. Incident Management (`/incidents` & `/incidents/:id`)
- **Search & Multi-Attribute Filters**: Search by full or partial UUID, original sender device ID, gateway ID, or reported description text. Filter by priority (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`), category, and verification state.
- **CSV Export**: Real client-side generation of RFC 4180-compliant CSV exports with ISO timestamps.
- **Incident Details Deep Dive**:
  - Full cryptographic message payload hash.
  - Mesh hop traversal counter.
  - Origin sender device ID and ingestion gateway device ID.
  - Spatial coordinates with GPS accuracy radius in meters.
  - Interactive mini-map focused on incident location.
  - Verification history and operator notes.
  - Immutable emergency alert event log.

### 3.3. Incident Verification Workflow
Operators can evaluate incoming alerts directly:
1. **`VERIFIED`**: Confirms real incident, adjusts priority level, records officer assessment notes, and updates operator status to `TRIAGED`.
2. **`UNDER_REVIEW`**: Marks alert as actively being verified with field teams.
3. **`FALSE_REPORT`**: Requires a mandatory justification reason, logs an immutable audit event, and marks status as `CLOSED`.

### 3.4. Tactical Live Map (`/map`)
- Fullscreen MapLibre GL JS spatial display.
- Color-coded incident markers based on priority (`CRITICAL` = pulsing red, `HIGH` = amber, `MODERATE` = cyan).
- Interactive popups displaying incident category, hop count, coordinates, and link to detailed telemetry.
- Floating coverage badge showing count of mapped incidents and count of alerts lacking a GPS fix.

### 3.5. Response Team Management (`/teams`)
- Manages search & rescue squads, medical units, evacuation crews, and hazmat teams.
- Availability status tracker: `AVAILABLE`, `ASSIGNED`, `EN_ROUTE`, `ON_SCENE`, `COMPLETED`, `UNAVAILABLE`.
- One-click dispatching directly from incident tables or details pages.
- Registration modal for commissioning new emergency response units.

### 3.6. Public Emergency Warnings (`/warnings`)
- Implements the civil defense warning lifecycle:
  `DRAFT` -> `SUBMITTED_FOR_APPROVAL` -> `APPROVED` -> `PUBLISHED` -> `REVOKED` / `EXPIRED`.
- Multi-step approval: Operators draft notices; authorized publishers or admins review and broadcast live.
- Revocation with mandatory justification reason.

### 3.7. Analytics & Trends (`/analytics`)
- Aggregated metrics computed dynamically:
  - GPS coverage ratio (percentage of mesh alerts with captured GPS).
  - Mean hop traversal count across the store-carry-forward mesh network.
  - Active reporting gateway count.
  - Category breakdown bar chart.
  - Priority distribution donut chart.
  - 7-day daily incident ingestion trend line.
  - Mesh hop distribution chart.

### 3.8. Security Audit Logs (`/audit`)
- Immutable ledger tracking all operator logins, verifications, rejections, team dispatches, and public warning actions.
- Displays actor email, assigned role, target resource, action type, client IP address, and raw payload JSON.

---

## 4. Running the Web Application Locally

### Prerequisites
- Node.js v18+ (tested on Node v24.15.0)
- npm v9+

### 1-Click Launch (Windows)
Double-click [`open_website.bat`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/open_website.bat) in the project root folder. It will:
1. Verify Node.js is present.
2. Ensure dependencies are installed.
3. Automatically launch the development server.
4. Open your default web browser directly to `http://localhost:3000`.

### Manual CLI Start
```bash
# Navigate to web application directory
cd web

# Install dependencies (if not already done)
npm install

# Run Vite development server
npm run dev
```

The portal will start on `http://localhost:3000`.

### Running Tests
```bash
cd web
npm test
```

### Production Build
```bash
cd web
npm run build
```
Generates optimized static assets in `web/dist/`.

---

## 5. Deployment Options

### Option A: Cloudflare Pages
1. Push the repository to GitHub / GitLab.
2. In the Cloudflare Dashboard, create a **Pages** project connected to the repository.
3. Set the build configuration:
   - **Framework preset**: `Vite`
   - **Root directory**: `web`
   - **Build command**: `npm run build`
   - **Build output directory**: `dist`
4. Add Environment Variables:
   - `VITE_SUPABASE_URL`: Your Supabase Project URL
   - `VITE_SUPABASE_ANON_KEY`: Your Supabase Public Anonymous Key

### Option B: Cloudflare Tunnel (Self-Hosted Command Center)
To expose the local control room web server securely through an encrypted tunnel without opening firewall ports:
```bash
# 1. Start web server locally
cd web
npm run preview -- --port 3000

# 2. In another terminal, run cloudflared tunnel
cloudflared tunnel --url http://localhost:3000
```
This generates a secure, signed `trycloudflare.com` or custom domain HTTPS endpoint directly routing to your command center console.
