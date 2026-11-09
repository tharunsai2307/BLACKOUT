-- ============================================================================
-- BLACKOUT PLATFORM - COMPLETE CONSOLIDATED POSTGRESQL MIGRATIONS (PHASES 5 - 10)
-- Target: Supabase Dashboard SQL Editor
-- ============================================================================

-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101000000_phase5_central_backend.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 5 Central Backend PostgreSQL Migration
-- Target Database: Supabase PostgreSQL

-- 1. Enable UUID Extension if not already present
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Create Custom ENUM types
DO $$ BEGIN
    CREATE TYPE emergency_priority_enum AS ENUM ('CRITICAL', 'HIGH', 'MODERATE', 'LOW');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE ingestion_status_enum AS ENUM ('PENDING', 'ACCEPTED', 'DUPLICATE_IGNORED', 'REJECTED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE operator_status_enum AS ENUM ('NEW', 'ACKNOWLEDGED', 'TRIAGED', 'DISPATCHED', 'RESOLVED', 'CLOSED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. Table: device_registrations
CREATE TABLE IF NOT EXISTS public.device_registrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    device_status TEXT NOT NULL DEFAULT 'ACTIVE',
    app_version TEXT NOT NULL DEFAULT '1.0'
);

CREATE INDEX IF NOT EXISTS idx_device_registrations_device_id ON public.device_registrations(device_id);
CREATE INDEX IF NOT EXISTS idx_device_registrations_user_id ON public.device_registrations(user_id);

-- 4. Table: emergency_alerts
CREATE TABLE IF NOT EXISTS public.emergency_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL UNIQUE,
    original_sender_device_id TEXT NOT NULL,
    gateway_device_id TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN (
        'Medical Emergency',
        'Road Accident',
        'Trapped / Stranded',
        'Flood',
        'Fire',
        'Natural Disaster',
        'Personal Safety',
        'Other'
    )),
    description TEXT NOT NULL CHECK (length(description) > 0 AND length(description) <= 1000),
    latitude DOUBLE PRECISION CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    longitude DOUBLE PRECISION CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    accuracy_meters DOUBLE PRECISION CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0.0),
    location_timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    hop_count INTEGER NOT NULL DEFAULT 0 CHECK (hop_count >= 0 AND hop_count <= 7),
    priority emergency_priority_enum NOT NULL DEFAULT 'MODERATE',
    ingestion_status ingestion_status_enum NOT NULL DEFAULT 'ACCEPTED',
    operator_status operator_status_enum NOT NULL DEFAULT 'NEW',
    protocol_version INTEGER NOT NULL DEFAULT 1,
    payload_hash TEXT NOT NULL,
    CONSTRAINT check_expiration CHECK (expires_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_message_id ON public.emergency_alerts(message_id);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_created_at ON public.emergency_alerts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_priority ON public.emergency_alerts(priority);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_category ON public.emergency_alerts(category);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_operator_status ON public.emergency_alerts(operator_status);

-- 5. Table: gateway_upload_history
CREATE TABLE IF NOT EXISTS public.gateway_upload_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL,
    gateway_device_id TEXT NOT NULL,
    upload_attempted_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    upload_completed_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    upload_status TEXT NOT NULL CHECK (upload_status IN ('SUCCESS', 'DUPLICATE', 'REJECTED', 'FAILED')),
    error_code TEXT,
    response_reference TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_gateway_upload_history_msg ON public.gateway_upload_history(message_id);
CREATE INDEX IF NOT EXISTS idx_gateway_upload_history_gateway ON public.gateway_upload_history(gateway_device_id);

-- 6. Table: emergency_alert_events (Audit Log)
CREATE TABLE IF NOT EXISTS public.emergency_alert_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL,
    actor_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_emergency_alert_events_msg ON public.emergency_alert_events(message_id);

-- 7. Enable Row Level Security (RLS) on all tables
ALTER TABLE public.device_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gateway_upload_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_alert_events ENABLE ROW LEVEL SECURITY;

-- 8. Row Level Security Policies

-- Policy: device_registrations
-- Authenticated users can register or update their own device mapping
CREATE POLICY "Users can manage own device registrations"
ON public.device_registrations
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- Service role has full access
CREATE POLICY "Service role full access on device_registrations"
ON public.device_registrations
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- Policy: emergency_alerts
-- Gateways (authenticated or anonymous via Edge Function) insert through secure API.
-- Public / Anon: Can insert emergency alerts (for distress beacons) but CANNOT read private alerts
CREATE POLICY "Allow public insert of emergency alerts"
ON public.emergency_alerts
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Authenticated Users can view only alerts created by their registered device or operator role
CREATE POLICY "Users view own device alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    original_sender_device_id IN (
        SELECT device_id FROM public.device_registrations WHERE user_id = auth.uid()
    )
);

-- Service role full access on emergency_alerts (Used by Edge Functions and Verified Responders)
CREATE POLICY "Service role full access on emergency_alerts"
ON public.emergency_alerts
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- Policy: gateway_upload_history
CREATE POLICY "Allow insert of upload history"
ON public.gateway_upload_history
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

CREATE POLICY "Service role full access on gateway_upload_history"
ON public.gateway_upload_history
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- Policy: emergency_alert_events
CREATE POLICY "Service role full access on emergency_alert_events"
ON public.emergency_alert_events
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);


-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101010000_phase6_operations_portal.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 6 Emergency Operations Portal & Incident Response Backend Migration
-- Target Database: Supabase PostgreSQL

-- 1. Create Application Role ENUM
DO $$ BEGIN
    CREATE TYPE app_role_enum AS ENUM (
        'SUPER_ADMIN',
        'CONTROL_ROOM_OPERATOR',
        'FIELD_RESPONSE_COORDINATOR',
        'PUBLIC_WARNING_PUBLISHER',
        'VIEWER',
        'CITIZEN'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. Create Response Team ENUMs
DO $$ BEGIN
    CREATE TYPE team_status_enum AS ENUM (
        'AVAILABLE', 'ASSIGNED', 'EN_ROUTE', 'ON_SCENE', 'COMPLETED', 'UNAVAILABLE'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE team_type_enum AS ENUM (
        'SEARCH_AND_RESCUE', 'MEDICAL_RESPONSE', 'EVACUATION_CREW', 'FIREFIGHTING', 'HAZMAT', 'DISASTER_LOGISTICS', 'GENERAL'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. Create Public Warning ENUMs
DO $$ BEGIN
    CREATE TYPE warning_severity_enum AS ENUM (
        'IMMEDIATE THREAT', 'SEVERE', 'MODERATE', 'ADVISORY'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE warning_status_enum AS ENUM (
        'DRAFT', 'SUBMITTED_FOR_APPROVAL', 'APPROVED', 'PUBLISHED', 'EXPIRED', 'REVOKED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 4. User Profiles Table (Linked to auth.users)
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role app_role_enum NOT NULL DEFAULT 'CITIZEN',
    badge_number TEXT,
    organization TEXT NOT NULL DEFAULT 'BLACKOUT Emergency Management Operations',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_role ON public.user_profiles(role);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON public.user_profiles(email);

-- Compatibility view mapping user_roles to user_profiles
CREATE OR REPLACE VIEW public.user_roles AS
    SELECT id AS user_id, role, organization FROM public.user_profiles;

-- 5. Response Teams Table
CREATE TABLE IF NOT EXISTS public.response_teams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    callsign TEXT NOT NULL UNIQUE,
    team_type team_type_enum NOT NULL DEFAULT 'GENERAL',
    status team_status_enum NOT NULL DEFAULT 'AVAILABLE',
    contact_phone TEXT,
    base_location TEXT,
    capacity INTEGER NOT NULL DEFAULT 4,
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    active_incident_id UUID REFERENCES public.emergency_alerts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_response_teams_status ON public.response_teams(status);
CREATE INDEX IF NOT EXISTS idx_response_teams_type ON public.response_teams(team_type);

-- 6. Response Team Members Table
CREATE TABLE IF NOT EXISTS public.response_team_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    team_id UUID NOT NULL REFERENCES public.response_teams(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    role_title TEXT NOT NULL DEFAULT 'Operator',
    phone TEXT,
    badge_number TEXT,
    is_team_lead BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_team_members_team_id ON public.response_team_members(team_id);

-- 7. Extend emergency_alerts with Incident Management Fields
ALTER TABLE public.emergency_alerts
ADD COLUMN IF NOT EXISTS verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED'
    CHECK (verification_status IN ('UNVERIFIED', 'UNDER_REVIEW', 'VERIFIED', 'FALSE_REPORT')),
ADD COLUMN IF NOT EXISTS assigned_team_id UUID REFERENCES public.response_teams(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS verified_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS operator_notes TEXT,
ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS resolved_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now());

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_verification ON public.emergency_alerts(verification_status);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_assigned_team ON public.emergency_alerts(assigned_team_id);

-- 8. Public Warnings Table
CREATE TABLE IF NOT EXISTS public.public_warnings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warning_code TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL CHECK (length(trim(title)) > 0 AND length(title) <= 200),
    description TEXT NOT NULL CHECK (length(trim(description)) > 0 AND length(description) <= 2000),
    category TEXT NOT NULL CHECK (category IN (
        'Flood', 'Cyclone', 'Heavy rainfall', 'Landslide', 'Earthquake',
        'Fire', 'Evacuation', 'Infrastructure emergency', 'General public safety'
    )),
    severity warning_severity_enum NOT NULL DEFAULT 'MODERATE',
    affected_area TEXT NOT NULL CHECK (length(trim(affected_area)) > 0),
    latitude DOUBLE PRECISION CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    longitude DOUBLE PRECISION CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    radius_km DOUBLE PRECISION CHECK (radius_km IS NULL OR radius_km >= 0.0),
    issuing_authority TEXT NOT NULL DEFAULT 'BLACKOUT Civil Defense & Emergency Response Administration',
    status warning_status_enum NOT NULL DEFAULT 'DRAFT',
    instructions TEXT,
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_public_warnings_status ON public.public_warnings(status);
CREATE INDEX IF NOT EXISTS idx_public_warnings_severity ON public.public_warnings(severity);
CREATE INDEX IF NOT EXISTS idx_public_warnings_category ON public.public_warnings(category);

-- 9. Comprehensive System Audit Logs Table (Immutable)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    actor_email TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    action TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON public.audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON public.audit_logs(resource_type, resource_id);

-- 10. Helper Functions for RBAC (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.get_current_user_role()
RETURNS text AS $$
DECLARE
    user_role text;
BEGIN
    SELECT role::text INTO user_role
    FROM public.user_profiles
    WHERE id = auth.uid() AND is_active = true;
    RETURN COALESCE(user_role, 'VIEWER');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.has_role(required_role text)
RETURNS boolean AS $$
BEGIN
    RETURN (SELECT public.get_current_user_role() = required_role);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_operator_or_admin()
RETURNS boolean AS $$
DECLARE
    cur_role text;
BEGIN
    cur_role := public.get_current_user_role();
    RETURN cur_role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 11. Enable Row Level Security (RLS)
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.response_teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.response_team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.public_warnings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 12. RLS Policies: user_profiles
CREATE POLICY "Authorized personnel can view user profiles"
ON public.user_profiles
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "Users can update their own personal info"
ON public.user_profiles
FOR UPDATE
TO authenticated
USING (id = auth.uid())
WITH CHECK (
    id = auth.uid() AND
    role = (SELECT role FROM public.user_profiles WHERE id = auth.uid())
);

CREATE POLICY "Super Admins can manage all user profiles"
ON public.user_profiles
FOR ALL
TO authenticated
USING (public.get_current_user_role() = 'SUPER_ADMIN')
WITH CHECK (public.get_current_user_role() = 'SUPER_ADMIN');

CREATE POLICY "Service role full access on user_profiles"
ON public.user_profiles
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 13. RLS Policies: emergency_alerts (Operational Staff Permissions)
CREATE POLICY "Authorized personnel can view all emergency alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR', 'PUBLIC_WARNING_PUBLISHER', 'VIEWER')
);

CREATE POLICY "Operators and Admins can update emergency alerts"
ON public.emergency_alerts
FOR UPDATE
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
)
WITH CHECK (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
);

-- 14. RLS Policies: response_teams & response_team_members
CREATE POLICY "Authorized staff can view response teams"
ON public.response_teams
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "Operators and Admins can manage response teams"
ON public.response_teams
FOR ALL
TO authenticated
USING (public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR'))
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR'));

CREATE POLICY "Service role full access on response_teams"
ON public.response_teams
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY "Authorized staff can view response team members"
ON public.response_team_members
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "Operators and Admins can manage team members"
ON public.response_team_members
FOR ALL
TO authenticated
USING (public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR'))
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR'));

CREATE POLICY "Service role full access on response_team_members"
ON public.response_team_members
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 15. RLS Policies: public_warnings
-- Public / Android App / Anon: CAN ONLY VIEW PUBLISHED NON-EXPIRED WARNINGS
CREATE POLICY "Public and Android app can read published active warnings"
ON public.public_warnings
FOR SELECT
TO anon, authenticated
USING (
    status = 'PUBLISHED' AND expires_at > timezone('utc'::text, now())
);

-- Authorized operations personnel can view all warnings (drafts, pending, revoked)
CREATE POLICY "Authorized personnel can view all warnings"
ON public.public_warnings
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'PUBLIC_WARNING_PUBLISHER', 'VIEWER')
);

-- Publishers and Admins can create and update warnings
CREATE POLICY "Publishers and Admins can manage warnings"
ON public.public_warnings
FOR ALL
TO authenticated
USING (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER', 'CONTROL_ROOM_OPERATOR'))
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER', 'CONTROL_ROOM_OPERATOR'));

CREATE POLICY "Service role full access on public_warnings"
ON public.public_warnings
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 16. RLS Policies: audit_logs (Immutable Ledger)
CREATE POLICY "Authorized personnel can read audit logs"
ON public.audit_logs
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'VIEWER')
);

CREATE POLICY "Authenticated users can write audit logs"
ON public.audit_logs
FOR INSERT
TO authenticated
WITH CHECK (true);

CREATE POLICY "Service role full access on audit_logs"
ON public.audit_logs
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- NO UPDATE OR DELETE POLICIES ON audit_logs (Enforces Immutability)

-- 17. Enable Realtime Replication
DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.emergency_alerts;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.public_warnings;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.response_teams;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.audit_logs;
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- 18. Seed Initial Default Response Teams (BLACKOUT-Managed Operations Units)
INSERT INTO public.response_teams (name, callsign, team_type, status, contact_phone, base_location, capacity)
VALUES
    ('Alpha Rapid Search & Rescue', 'SAR-ALPHA', 'SEARCH_AND_RESCUE', 'AVAILABLE', '+1-800-555-0101', 'Sector 1 Command Post', 6),
    ('Bravo Emergency Medical Corps', 'MED-BRAVO', 'MEDICAL_RESPONSE', 'AVAILABLE', '+1-800-555-0102', 'Central Field Hospital', 4),
    ('Charlie Evacuation Transport', 'EVAC-CHARLIE', 'EVACUATION_CREW', 'AVAILABLE', '+1-800-555-0103', 'North Transport Staging', 8),
    ('Delta Urban Firefighting Unit', 'FIRE-DELTA', 'FIREFIGHTING', 'AVAILABLE', '+1-800-555-0104', 'Station 4 Tactical Base', 5),
    ('Echo Hazardous Materials Squad', 'HAZ-ECHO', 'HAZMAT', 'AVAILABLE', '+1-800-555-0105', 'South Logistics Depot', 4)
ON CONFLICT (name) DO NOTHING;

-- Seed Team Members
INSERT INTO public.response_team_members (team_id, full_name, role_title, phone, badge_number, is_team_lead)
SELECT id, 'Capt. Marcus Vance', 'Search & Rescue Lead', '+1-555-0191', 'SAR-001', true
FROM public.response_teams WHERE callsign = 'SAR-ALPHA'
ON CONFLICT DO NOTHING;

INSERT INTO public.response_team_members (team_id, full_name, role_title, phone, badge_number, is_team_lead)
SELECT id, 'Dr. Sarah Lin', 'Lead Medical Officer', '+1-555-0192', 'MED-001', true
FROM public.response_teams WHERE callsign = 'MED-BRAVO'
ON CONFLICT DO NOTHING;


-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101020000_phase7_signed_public_warnings.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 7 Digitally Signed Public Warnings & Key Registry Migration
-- Target Database: Supabase PostgreSQL

-- 1. Create Warning Signing Keys Registry Table
CREATE TABLE IF NOT EXISTS public.warning_signing_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_id TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL, -- Raw 32-byte Ed25519 public key encoded in Base64
    algorithm TEXT NOT NULL DEFAULT 'Ed25519',
    authority_id TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    key_status TEXT NOT NULL CHECK (key_status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_signing_keys_status ON public.warning_signing_keys(key_status);
CREATE INDEX IF NOT EXISTS idx_signing_keys_key_id ON public.warning_signing_keys(key_id);

-- 2. Extend public_warnings Table with Cryptographic and Protocol Fields
ALTER TABLE public.public_warnings
ADD COLUMN IF NOT EXISTS protocol_version INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS signing_key_id TEXT REFERENCES public.warning_signing_keys(key_id) ON DELETE RESTRICT,
ADD COLUMN IF NOT EXISTS signature_algorithm TEXT NOT NULL DEFAULT 'Ed25519',
ADD COLUMN IF NOT EXISTS signature TEXT, -- Base64 64-byte Ed25519 digital signature
ADD COLUMN IF NOT EXISTS canonical_payload TEXT, -- Deterministic signed representation
ADD COLUMN IF NOT EXISTS affected_regions JSONB NOT NULL DEFAULT '[]'::jsonb,
ADD COLUMN IF NOT EXISTS radius_meters DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'en',
ADD COLUMN IF NOT EXISTS effective_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now());

CREATE INDEX IF NOT EXISTS idx_public_warnings_key_id ON public.public_warnings(signing_key_id);
CREATE INDEX IF NOT EXISTS idx_public_warnings_revision ON public.public_warnings(warning_code, revision);

-- 3. Create Warning Revisions Table (Immutable Revision History)
CREATE TABLE IF NOT EXISTS public.warning_revisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warning_id UUID NOT NULL REFERENCES public.public_warnings(id) ON DELETE CASCADE,
    warning_code TEXT NOT NULL,
    revision INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL,
    severity TEXT NOT NULL,
    status TEXT NOT NULL,
    issuing_authority TEXT NOT NULL,
    signing_key_id TEXT NOT NULL REFERENCES public.warning_signing_keys(key_id) ON DELETE RESTRICT,
    signature_algorithm TEXT NOT NULL DEFAULT 'Ed25519',
    signature TEXT NOT NULL,
    canonical_payload TEXT NOT NULL,
    affected_regions JSONB NOT NULL DEFAULT '[]'::jsonb,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    radius_meters DOUBLE PRECISION,
    language TEXT NOT NULL DEFAULT 'en',
    issued_at TIMESTAMPTZ NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_warning_revision UNIQUE (warning_id, revision)
);

CREATE INDEX IF NOT EXISTS idx_warning_revisions_warning_id ON public.warning_revisions(warning_id);
CREATE INDEX IF NOT EXISTS idx_warning_revisions_code_rev ON public.warning_revisions(warning_code, revision);

-- 4. Create Warning Revocations Table
CREATE TABLE IF NOT EXISTS public.warning_revocations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warning_id UUID NOT NULL REFERENCES public.public_warnings(id) ON DELETE CASCADE,
    warning_code TEXT NOT NULL,
    revision INTEGER NOT NULL,
    revoked_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    revocation_reason TEXT NOT NULL,
    revocation_signature TEXT,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_warning_revocations_warning_id ON public.warning_revocations(warning_id);

-- 5. Enable Row Level Security (RLS)
ALTER TABLE public.warning_signing_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.warning_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.warning_revocations ENABLE ROW LEVEL SECURITY;

-- 6. RLS Policies: warning_signing_keys
-- Public & Mobile Clients can read all public keys for offline verification
CREATE POLICY "Public and Android app can read signing keys"
ON public.warning_signing_keys
FOR SELECT
TO anon, authenticated
USING (true);

-- Only Super Admins can insert/update signing keys
CREATE POLICY "Super Admins can manage signing keys"
ON public.warning_signing_keys
FOR ALL
TO authenticated
USING (public.get_current_user_role() = 'SUPER_ADMIN')
WITH CHECK (public.get_current_user_role() = 'SUPER_ADMIN');

CREATE POLICY "Service role full access on warning_signing_keys"
ON public.warning_signing_keys
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 7. RLS Policies: warning_revisions
CREATE POLICY "Public and Android app can read published revisions"
ON public.warning_revisions
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Publishers and Admins can create revisions"
ON public.warning_revisions
FOR INSERT
TO authenticated
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER'));

CREATE POLICY "Service role full access on warning_revisions"
ON public.warning_revisions
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 8. RLS Policies: warning_revocations
CREATE POLICY "Public and Android app can read revocations"
ON public.warning_revocations
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Publishers and Admins can create revocations"
ON public.warning_revocations
FOR INSERT
TO authenticated
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER'));

CREATE POLICY "Service role full access on warning_revocations"
ON public.warning_revocations
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 9. Realtime Replication for Warnings & Keys
DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_signing_keys;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_revisions;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_revocations;
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- 10. Seed Initial Root Verification Key
-- Key: KEY-BLACKOUT-ROOT-2026 (RFC 8032 Ed25519)
-- Authority: BLACKOUT Civil Defense & Emergency Response Administration
INSERT INTO public.warning_signing_keys (
    key_id,
    public_key,
    algorithm,
    authority_id,
    valid_from,
    valid_until,
    key_status
) VALUES (
    'KEY-BLACKOUT-ROOT-2026',
    'k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=',
    'Ed25519',
    'BLACKOUT Civil Defense & Emergency Response Administration',
    '2026-01-01T00:00:00Z',
    '2036-01-01T00:00:00Z',
    'ACTIVE'
) ON CONFLICT (key_id) DO NOTHING;



-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101030000_phase9_geoapify_and_sms.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 9 Add-On Migration: Geoapify Location Enrichment & Provider-Independent SMS Notifications
-- Target Database: Supabase PostgreSQL 15+

-- ============================================================================
-- 1. EXTEND EMERGENCY_ALERTS TABLE FOR REVERSE GEOCODING ENRICHMENT
-- ============================================================================

ALTER TABLE public.emergency_alerts
ADD COLUMN IF NOT EXISTS geocoded_address TEXT,
ADD COLUMN IF NOT EXISTS geocoded_locality TEXT,
ADD COLUMN IF NOT EXISTS geocoded_district TEXT,
ADD COLUMN IF NOT EXISTS geocoded_state TEXT,
ADD COLUMN IF NOT EXISTS geocoded_country TEXT,
ADD COLUMN IF NOT EXISTS geocoded_postal_code TEXT,
ADD COLUMN IF NOT EXISTS geocoded_is_approximate BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS geocoded_confidence DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS geocoded_at TIMESTAMPTZ;

-- Index for searching geocoded localities/districts
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_geocoded_locality ON public.emergency_alerts(geocoded_locality);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_geocoded_district ON public.emergency_alerts(geocoded_district);

-- ============================================================================
-- 2. RECIPIENT PREFERENCES & CONSENT TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.sms_recipient_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number TEXT NOT NULL UNIQUE CHECK (phone_number ~ '^\+[1-9]\d{1,14}$'), -- E.164 phone format
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    has_emergency_contact_consent BOOLEAN NOT NULL DEFAULT FALSE,
    has_public_warning_consent BOOLEAN NOT NULL DEFAULT FALSE,
    verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'REVOKED')),
    consent_source TEXT NOT NULL DEFAULT 'OPERATOR_VERIFIED' CHECK (consent_source IN ('USER_APP_ONBOARDING', 'OPERATOR_VERIFIED', 'WRITTEN_MOU', 'CIVIL_DEFENSE_REGISTRY')),
    consent_timestamp TIMESTAMPTZ,
    opted_out_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_sms_recipient_phone ON public.sms_recipient_preferences(phone_number);
CREATE INDEX IF NOT EXISTS idx_sms_recipient_user ON public.sms_recipient_preferences(user_id);

-- ============================================================================
-- 3. SMS PROVIDER CONFIGURATION METADATA TABLE (NO PLAINTEXT SECRETS)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.sms_provider_configuration (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_name TEXT NOT NULL UNIQUE CHECK (provider_name IN ('MOCK_PROVIDER', 'GENERIC_WEBHOOK', 'TWILIO_SMS', 'INDIAN_DLT_PROVIDER')),
    display_name TEXT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    rate_limit_per_minute INT NOT NULL DEFAULT 30 CHECK (rate_limit_per_minute > 0 AND rate_limit_per_minute <= 300),
    supported_regions TEXT[] NOT NULL DEFAULT ARRAY['IN']::TEXT[],
    dlt_entity_id TEXT, -- TRAI DLT Principal Entity ID
    dlt_sender_id TEXT, -- 6-alpha Header e.g. BLKOUT
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb, -- Provider capabilities metadata (no credentials)
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- Seed default provider configuration records (Secrets remain in Edge Function environment)
INSERT INTO public.sms_provider_configuration (provider_name, display_name, is_enabled, is_primary, rate_limit_per_minute, supported_regions, dlt_sender_id)
VALUES 
    ('MOCK_PROVIDER', 'Simulated Local/Dev Provider', TRUE, TRUE, 60, ARRAY['IN', 'GLOBAL'], 'BLKOUT'),
    ('INDIAN_DLT_PROVIDER', 'TRAI DLT Production Gateway (Airtel/Jio/BSNL)', FALSE, FALSE, 30, ARRAY['IN'], 'BLKOPS'),
    ('GENERIC_WEBHOOK', 'Authorized Webhook Gateway', FALSE, FALSE, 30, ARRAY['GLOBAL'], 'ALERT')
ON CONFLICT (provider_name) DO NOTHING;

-- ============================================================================
-- 4. SMS NOTIFICATION REQUESTS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.sms_notification_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    request_id UUID NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
    idempotency_key TEXT NOT NULL UNIQUE,
    scenario TEXT NOT NULL CHECK (scenario IN ('EMERGENCY_CONTACT_ALERT', 'OPERATOR_INCIDENT_DISPATCH', 'PUBLIC_WARNING_BROADCAST')),
    incident_id UUID REFERENCES public.emergency_alerts(id) ON DELETE SET NULL,
    warning_id UUID REFERENCES public.public_warnings(id) ON DELETE SET NULL,
    recipient_phone TEXT NOT NULL CHECK (recipient_phone ~ '^\+[1-9]\d{1,14}$'),
    template_id TEXT NOT NULL,
    template_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    sanitized_message_preview TEXT NOT NULL, -- PII-masked message preview for audit
    status TEXT NOT NULL CHECK (status IN ('QUEUED', 'SUBMITTED', 'PROVIDER_ACCEPTED', 'DELIVERED', 'FAILED', 'UNKNOWN')) DEFAULT 'QUEUED',
    priority TEXT NOT NULL DEFAULT 'HIGH' CHECK (priority IN ('CRITICAL', 'HIGH', 'MODERATE', 'LOW')),
    requested_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    requested_by_role TEXT NOT NULL,
    attempts_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_sms_requests_recipient ON public.sms_notification_requests(recipient_phone);
CREATE INDEX IF NOT EXISTS idx_sms_requests_incident ON public.sms_notification_requests(incident_id);
CREATE INDEX IF NOT EXISTS idx_sms_requests_status ON public.sms_notification_requests(status);
CREATE INDEX IF NOT EXISTS idx_sms_requests_idempotency ON public.sms_notification_requests(idempotency_key);

-- ============================================================================
-- 5. SMS DELIVERY ATTEMPTS LOG TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.sms_delivery_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_request_id UUID NOT NULL REFERENCES public.sms_notification_requests(id) ON DELETE CASCADE,
    provider_name TEXT NOT NULL,
    attempt_number INT NOT NULL CHECK (attempt_number >= 1),
    status TEXT NOT NULL CHECK (status IN ('SUBMITTED', 'PROVIDER_ACCEPTED', 'DELIVERED', 'FAILED', 'UNKNOWN')),
    provider_reference TEXT,
    error_code TEXT,
    error_message TEXT, -- Sanitized error string with zero PII
    latency_ms INT CHECK (latency_ms >= 0),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_sms_attempts_request ON public.sms_delivery_attempts(notification_request_id);

-- ============================================================================
-- 6. ROW-LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

ALTER TABLE public.sms_recipient_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_provider_configuration ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_notification_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_delivery_attempts ENABLE ROW LEVEL SECURITY;

-- sms_recipient_preferences: Read/Write restricted to authenticated staff
DROP POLICY IF EXISTS sms_recipient_preferences_select ON public.sms_recipient_preferences;
CREATE POLICY sms_recipient_preferences_select ON public.sms_recipient_preferences
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

DROP POLICY IF EXISTS sms_recipient_preferences_insert_update ON public.sms_recipient_preferences;
CREATE POLICY sms_recipient_preferences_insert_update ON public.sms_recipient_preferences
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR')
        )
    );

-- sms_provider_configuration: Read-only for authenticated operators, write for SUPER_ADMIN
DROP POLICY IF EXISTS sms_provider_configuration_select ON public.sms_provider_configuration;
CREATE POLICY sms_provider_configuration_select ON public.sms_provider_configuration
    FOR SELECT TO authenticated
    USING (true);

DROP POLICY IF EXISTS sms_provider_configuration_admin ON public.sms_provider_configuration;
CREATE POLICY sms_provider_configuration_admin ON public.sms_provider_configuration
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role = 'SUPER_ADMIN'
        )
    );

-- sms_notification_requests: Authorized personnel can view and queue
DROP POLICY IF EXISTS sms_notification_requests_select ON public.sms_notification_requests;
CREATE POLICY sms_notification_requests_select ON public.sms_notification_requests
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

DROP POLICY IF EXISTS sms_notification_requests_insert ON public.sms_notification_requests;
CREATE POLICY sms_notification_requests_insert ON public.sms_notification_requests
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

-- sms_delivery_attempts: Read-only audit log for authorized staff
DROP POLICY IF EXISTS sms_delivery_attempts_select ON public.sms_delivery_attempts;
CREATE POLICY sms_delivery_attempts_select ON public.sms_delivery_attempts
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up 
            WHERE up.id = auth.uid() 
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );


-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101040000_phase9_5_auth_and_profiles.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 9.5 Migration: Unified Authentication, User Profiles & Emergency Contacts
-- Target Database: Supabase PostgreSQL 15+

-- ============================================================================
-- 1. EXTEND APP ROLE ENUM FOR CITIZEN USERS
-- ============================================================================

DO $$ BEGIN
    ALTER TYPE public.app_role_enum ADD VALUE IF NOT EXISTS 'CITIZEN';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- ============================================================================
-- 2. EXTEND USER PROFILES TABLE (SUPPORTING CITIZENS & OPERATORS)
-- ============================================================================

ALTER TABLE public.user_profiles
ADD COLUMN IF NOT EXISTS phone_number TEXT,
ADD COLUMN IF NOT EXISTS country_code TEXT DEFAULT '+91',
ADD COLUMN IF NOT EXISTS country_region TEXT,
ADD COLUMN IF NOT EXISTS profile_photo_url TEXT,
ADD COLUMN IF NOT EXISTS preferred_language TEXT DEFAULT 'en',
ADD COLUMN IF NOT EXISTS medical_notes TEXT,
ADD COLUMN IF NOT EXISTS profile_completed BOOLEAN DEFAULT FALSE;

-- Ensure default role for new citizen accounts is CITIZEN (safe against 55P04)
DO $$ BEGIN
    ALTER TABLE public.user_profiles
    ALTER COLUMN role SET DEFAULT 'CITIZEN'::public.app_role_enum;
EXCEPTION
    WHEN unsafe_new_enum_value_usage OR others THEN
        -- Role default is already established in phase 6 schema
        null;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_profiles_phone ON public.user_profiles(phone_number);

-- ============================================================================
-- 3. EMERGENCY CONTACTS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.emergency_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    contact_name TEXT NOT NULL CHECK (length(trim(contact_name)) > 0 AND length(contact_name) <= 100),
    phone_number TEXT NOT NULL CHECK (phone_number ~ '^\+[1-9]\d{1,14}$'), -- E.164 international format
    relationship TEXT NOT NULL DEFAULT 'Other' CHECK (relationship IN ('Parent', 'Spouse', 'Child', 'Sibling', 'Friend', 'Colleague', 'Other')),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_emergency_contacts_user_id ON public.emergency_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_contacts_is_primary ON public.emergency_contacts(is_primary);

-- ============================================================================
-- 4. ROW-LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_contacts ENABLE ROW LEVEL SECURITY;

-- 4.1 user_profiles RLS Policies
-- Users can view their own profile
DROP POLICY IF EXISTS user_profiles_select_own ON public.user_profiles;
CREATE POLICY user_profiles_select_own ON public.user_profiles
    FOR SELECT TO authenticated
    USING (auth.uid() = id);

-- Operators and Admins can view profiles for active incident response verification
DROP POLICY IF EXISTS user_profiles_select_operator ON public.user_profiles;
CREATE POLICY user_profiles_select_operator ON public.user_profiles
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up
            WHERE up.id = auth.uid()
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

-- Users can update only their own profile
DROP POLICY IF EXISTS user_profiles_update_own ON public.user_profiles;
CREATE POLICY user_profiles_update_own ON public.user_profiles
    FOR UPDATE TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Users can insert their own profile upon registration
DROP POLICY IF EXISTS user_profiles_insert_own ON public.user_profiles;
CREATE POLICY user_profiles_insert_own ON public.user_profiles
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = id);

-- 4.2 emergency_contacts RLS Policies
-- Users can view their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_select_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_select_own ON public.emergency_contacts
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

-- Incident commanders can view contacts for victims of verified incidents during active rescue
DROP POLICY IF EXISTS emergency_contacts_select_operator ON public.emergency_contacts;
CREATE POLICY emergency_contacts_select_operator ON public.emergency_contacts
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up
            WHERE up.id = auth.uid()
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR')
        )
    );

-- Users can insert their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_insert_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_insert_own ON public.emergency_contacts
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

-- Users can update their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_update_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_update_own ON public.emergency_contacts
    FOR UPDATE TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Users can delete their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_delete_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_delete_own ON public.emergency_contacts
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- ============================================================================
-- 5. AUTOMATIC PROFILE CREATION TRIGGER (AUTH.USERS HOOK)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user_registration()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_profiles (
        id,
        email,
        full_name,
        phone_number,
        role,
        organization,
        is_active,
        created_at,
        updated_at
    ) VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', 'BLACKOUT Citizen'),
        NEW.raw_user_meta_data->>'phone',
        'CITIZEN'::public.app_role_enum,
        'BLACKOUT Civilian Network',
        true,
        now(),
        now()
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger on auth.users after insert
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user_registration();




-- ----------------------------------------------------------------------------
-- MIGRATION: 20261101050000_phase10_fix_sos_upload_rls.sql
-- ----------------------------------------------------------------------------

-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 10 Migration: Secure Server-Side SOS Ingestion RPC & RLS Hardening
-- Target Database: Supabase PostgreSQL 15+

CREATE OR REPLACE FUNCTION public.submit_emergency_alert(payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_message_id UUID;
    v_original_sender TEXT;
    v_gateway_device TEXT;
    v_category TEXT;
    v_description TEXT;
    v_priority public.emergency_priority_enum;
    v_latitude DOUBLE PRECISION;
    v_longitude DOUBLE PRECISION;
    v_accuracy DOUBLE PRECISION;
    v_location_timestamp TIMESTAMPTZ;
    v_created_at TIMESTAMPTZ;
    v_expires_at TIMESTAMPTZ;
    v_hop_count INTEGER;
    v_protocol_version INTEGER;
    v_payload_hash TEXT;
    v_existing_id UUID;
    v_existing_hash TEXT;
    v_new_id UUID;
    v_recent_alert_count INTEGER;
    v_user_id UUID;
BEGIN
    -- 1. Protocol Version Validation
    v_protocol_version := COALESCE((payload->>'protocolVersion')::INTEGER, (payload->>'protocol_version')::INTEGER, 1);
    IF v_protocol_version != 1 THEN
        RAISE EXCEPTION 'UNSUPPORTED_PROTOCOL_VERSION: Protocol version % is not supported', v_protocol_version
            USING ERRCODE = '22000';
    END IF;

    -- 2. Message UUID Validation
    BEGIN
        v_message_id := (COALESCE(payload->>'messageId', payload->>'message_id'))::UUID;
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'INVALID_MESSAGE_ID: messageId must be a valid RFC 4122 UUID'
            USING ERRCODE = '22023';
    END;

    IF v_message_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_MESSAGE_ID: messageId cannot be null'
            USING ERRCODE = '22023';
    END IF;

    -- 3. Device Identification
    v_original_sender := NULLIF(TRIM(COALESCE(
        payload->>'originalSenderDeviceId',
        payload->>'original_sender_device_id',
        payload->>'senderDeviceId',
        'UNKNOWN_DEV'
    )), '');
    IF v_original_sender IS NULL THEN
        v_original_sender := 'UNKNOWN_DEV';
    END IF;

    v_gateway_device := NULLIF(TRIM(COALESCE(
        payload->>'gatewayDeviceId',
        payload->>'gateway_device_id',
        'GATEWAY-UNKNOWN'
    )), '');
    IF v_gateway_device IS NULL THEN
        v_gateway_device := 'GATEWAY-UNKNOWN';
    END IF;

    -- 4. Category Validation
    v_category := TRIM(COALESCE(payload->>'category', 'Other'));
    IF v_category NOT IN (
        'Medical Emergency',
        'Road Accident',
        'Trapped / Stranded',
        'Flood',
        'Fire',
        'Natural Disaster',
        'Personal Safety',
        'Other'
    ) THEN
        RAISE EXCEPTION 'INVALID_CATEGORY: "%" is not a valid emergency category', v_category
            USING ERRCODE = '22023';
    END IF;

    -- 5. Description Validation
    v_description := TRIM(COALESCE(payload->>'description', ''));
    IF length(v_description) = 0 OR length(v_description) > 1000 THEN
        RAISE EXCEPTION 'INVALID_DESCRIPTION: Description length must be between 1 and 1000 characters'
            USING ERRCODE = '22023';
    END IF;

    -- 6. Priority Validation & Mapping
    BEGIN
        v_priority := UPPER(TRIM(COALESCE(payload->>'priority', 'MODERATE')))::public.emergency_priority_enum;
    EXCEPTION WHEN OTHERS THEN
        v_priority := 'MODERATE'::public.emergency_priority_enum;
    END;

    -- 7. Coordinates Validation
    IF (payload->>'latitude') IS NOT NULL AND TRIM(payload->>'latitude') != '' THEN
        v_latitude := (payload->>'latitude')::DOUBLE PRECISION;
        IF v_latitude < -90.0 OR v_latitude > 90.0 THEN
            RAISE EXCEPTION 'INVALID_LATITUDE: Latitude must be in [-90.0, 90.0]'
                USING ERRCODE = '22023';
        END IF;
    END IF;

    IF (payload->>'longitude') IS NOT NULL AND TRIM(payload->>'longitude') != '' THEN
        v_longitude := (payload->>'longitude')::DOUBLE PRECISION;
        IF v_longitude < -180.0 OR v_longitude > 180.0 THEN
            RAISE EXCEPTION 'INVALID_LONGITUDE: Longitude must be in [-180.0, 180.0]'
                USING ERRCODE = '22023';
        END IF;
    END IF;

    IF (payload->>'accuracyMeters') IS NOT NULL AND TRIM(payload->>'accuracyMeters') != '' THEN
        v_accuracy := (payload->>'accuracyMeters')::DOUBLE PRECISION;
    ELSIF (payload->>'accuracy_meters') IS NOT NULL AND TRIM(payload->>'accuracy_meters') != '' THEN
        v_accuracy := (payload->>'accuracy_meters')::DOUBLE PRECISION;
    END IF;

    IF (payload->>'locationTimestamp') IS NOT NULL AND TRIM(payload->>'locationTimestamp') != '' THEN
        BEGIN
            IF (payload->>'locationTimestamp') ~ '^\d+$' THEN
                v_location_timestamp := to_timestamp((payload->>'locationTimestamp')::DOUBLE PRECISION / 1000.0);
            ELSE
                v_location_timestamp := (payload->>'locationTimestamp')::TIMESTAMPTZ;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            v_location_timestamp := NULL;
        END;
    ELSIF (payload->>'location_timestamp') IS NOT NULL AND TRIM(payload->>'location_timestamp') != '' THEN
        BEGIN
            v_location_timestamp := (payload->>'location_timestamp')::TIMESTAMPTZ;
        EXCEPTION WHEN OTHERS THEN
            v_location_timestamp := NULL;
        END;
    END IF;

    -- 8. Timestamps and Hops Validation
    IF (payload->>'createdAt') IS NOT NULL AND TRIM(payload->>'createdAt') != '' THEN
        IF (payload->>'createdAt') ~ '^\d+$' THEN
            v_created_at := to_timestamp((payload->>'createdAt')::DOUBLE PRECISION / 1000.0);
        ELSE
            v_created_at := (payload->>'createdAt')::TIMESTAMPTZ;
        END IF;
    ELSIF (payload->>'created_at') IS NOT NULL AND TRIM(payload->>'created_at') != '' THEN
        v_created_at := (payload->>'created_at')::TIMESTAMPTZ;
    ELSE
        v_created_at := timezone('utc'::text, now());
    END IF;

    IF (payload->>'expiresAt') IS NOT NULL AND TRIM(payload->>'expiresAt') != '' THEN
        IF (payload->>'expiresAt') ~ '^\d+$' THEN
            v_expires_at := to_timestamp((payload->>'expiresAt')::DOUBLE PRECISION / 1000.0);
        ELSE
            v_expires_at := (payload->>'expiresAt')::TIMESTAMPTZ;
        END IF;
    ELSIF (payload->>'expires_at') IS NOT NULL AND TRIM(payload->>'expires_at') != '' THEN
        v_expires_at := (payload->>'expires_at')::TIMESTAMPTZ;
    ELSE
        v_expires_at := v_created_at + INTERVAL '48 hours';
    END IF;

    IF v_expires_at < v_created_at THEN
        v_expires_at := v_created_at + INTERVAL '48 hours';
    END IF;

    v_hop_count := COALESCE((payload->>'hopCount')::INTEGER, (payload->>'hop_count')::INTEGER, 0);
    IF v_hop_count < 0 OR v_hop_count > 7 THEN
        v_hop_count := LEAST(GREATEST(v_hop_count, 0), 7);
    END IF;

    v_payload_hash := COALESCE(payload->>'payloadHash', payload->>'payload_hash', encode(sha256(v_message_id::TEXT::bytea), 'hex'));

    -- 9. Rate Limiting Protection (Max 60 alerts/hr from same gateway device)
    SELECT COUNT(*) INTO v_recent_alert_count
    FROM public.gateway_upload_history
    WHERE gateway_device_id = v_gateway_device
      AND upload_attempted_at > timezone('utc'::text, now()) - INTERVAL '1 hour';

    IF v_recent_alert_count > 60 THEN
        RAISE EXCEPTION 'RATE_LIMIT_EXCEEDED: Gateway % exceeded upload limit (60 alerts/hr)', v_gateway_device
            USING ERRCODE = '22000';
    END IF;

    -- 10. Check Idempotency & Duplicate Handling
    SELECT id, payload_hash INTO v_existing_id, v_existing_hash
    FROM public.emergency_alerts
    WHERE message_id = v_message_id;

    IF v_existing_id IS NOT NULL THEN
        -- Log duplicate gateway upload
        INSERT INTO public.gateway_upload_history (
            message_id,
            gateway_device_id,
            upload_attempted_at,
            upload_completed_at,
            upload_status,
            response_reference
        ) VALUES (
            v_message_id,
            v_gateway_device,
            timezone('utc'::text, now()),
            timezone('utc'::text, now()),
            'DUPLICATE',
            v_existing_id::TEXT
        );

        RETURN jsonb_build_object(
            'status', 'DUPLICATE_ACCEPTED',
            'message', 'Emergency alert already recorded in central database',
            'messageId', v_message_id,
            'message_id', v_message_id,
            'backendReference', v_existing_id,
            'backend_reference', v_existing_id,
            'ingestionStatus', 'ACCEPTED'
        );
    END IF;

    -- 11. Caller User Context
    v_user_id := auth.uid();

    -- 12. Insert Alert Record
    INSERT INTO public.emergency_alerts (
        message_id,
        original_sender_device_id,
        gateway_device_id,
        category,
        description,
        latitude,
        longitude,
        accuracy_meters,
        location_timestamp,
        created_at,
        expires_at,
        received_at,
        hop_count,
        priority,
        ingestion_status,
        operator_status,
        protocol_version,
        payload_hash
    ) VALUES (
        v_message_id,
        v_original_sender,
        v_gateway_device,
        v_category,
        v_description,
        v_latitude,
        v_longitude,
        v_accuracy,
        v_location_timestamp,
        v_created_at,
        v_expires_at,
        timezone('utc'::text, now()),
        v_hop_count,
        v_priority,
        'ACCEPTED'::public.ingestion_status_enum,
        'NEW'::public.operator_status_enum,
        v_protocol_version,
        v_payload_hash
    )
    RETURNING id INTO v_new_id;

    -- 13. Log into Gateway Upload History
    INSERT INTO public.gateway_upload_history (
        message_id,
        gateway_device_id,
        upload_attempted_at,
        upload_completed_at,
        upload_status,
        response_reference
    ) VALUES (
        v_message_id,
        v_gateway_device,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        'SUCCESS',
        v_new_id::TEXT
    );

    -- 14. Log Audit Event in emergency_alert_events (if table exists)
    BEGIN
        INSERT INTO public.emergency_alert_events (
            message_id,
            actor_id,
            event_type,
            metadata
        ) VALUES (
            v_message_id,
            v_gateway_device,
            'ALERT_INGESTED_VIA_RPC',
            jsonb_build_object(
                'originalSender', v_original_sender,
                'gatewayDevice', v_gateway_device,
                'hopCount', v_hop_count,
                'userId', v_user_id,
                'priority', v_priority
            )
        );
    EXCEPTION WHEN OTHERS THEN
        null;
    END;

    RETURN jsonb_build_object(
        'status', 'SUCCESS',
        'message', 'Emergency alert successfully ingested and persisted in central database',
        'messageId', v_message_id,
        'message_id', v_message_id,
        'backendReference', v_new_id,
        'backend_reference', v_new_id,
        'ingestionStatus', 'ACCEPTED'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.submit_emergency_alert(jsonb) TO anon, authenticated, service_role;

ALTER TABLE public.emergency_alerts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public insert of emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Allow public insert of emergency alerts"
ON public.emergency_alerts
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS "Authorized personnel can view all emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Authorized personnel can view all emergency alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR', 'PUBLIC_WARNING_PUBLISHER', 'VIEWER')
);

DROP POLICY IF EXISTS "Users view own device alerts" ON public.emergency_alerts;
CREATE POLICY "Users view own device alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    original_sender_device_id IN (
        SELECT device_id FROM public.device_registrations WHERE user_id = auth.uid()
    )
);

CREATE OR REPLACE FUNCTION public.check_emergency_alert_status(p_message_id UUID)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_rec RECORD;
BEGIN
    SELECT id, ingestion_status, operator_status, created_at
    INTO v_rec
    FROM public.emergency_alerts
    WHERE message_id = p_message_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('exists', false);
    END IF;

    RETURN jsonb_build_object(
        'exists', true,
        'backend_reference', v_rec.id,
        'ingestion_status', v_rec.ingestion_status,
        'operator_status', v_rec.operator_status,
        'created_at', v_rec.created_at
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.check_emergency_alert_status(UUID) TO anon, authenticated, service_role;
