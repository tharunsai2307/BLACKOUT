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
