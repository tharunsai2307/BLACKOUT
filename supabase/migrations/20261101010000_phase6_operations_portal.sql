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
        'VIEWER'
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
    role app_role_enum NOT NULL DEFAULT 'VIEWER',
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
