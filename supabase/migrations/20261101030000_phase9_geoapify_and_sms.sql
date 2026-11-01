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
CREATE POLICY sms_recipient_preferences_select ON public.sms_recipient_preferences
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

CREATE POLICY sms_recipient_preferences_insert_update ON public.sms_recipient_preferences
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR')
        )
    );

-- sms_provider_configuration: Read-only for authenticated operators, write for SUPER_ADMIN
CREATE POLICY sms_provider_configuration_select ON public.sms_provider_configuration
    FOR SELECT TO authenticated
    USING (true);

CREATE POLICY sms_provider_configuration_admin ON public.sms_provider_configuration
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role = 'SUPER_ADMIN'
        )
    );

-- sms_notification_requests: Authorized personnel can view and queue
CREATE POLICY sms_notification_requests_select ON public.sms_notification_requests
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

CREATE POLICY sms_notification_requests_insert ON public.sms_notification_requests
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

-- sms_delivery_attempts: Read-only audit log for authorized staff
CREATE POLICY sms_delivery_attempts_select ON public.sms_delivery_attempts
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur 
            WHERE ur.user_id = auth.uid() 
            AND ur.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

