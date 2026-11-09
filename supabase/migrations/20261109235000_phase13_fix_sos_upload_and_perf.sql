-- ============================================================================
-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 13: Forward-Only Migration for SOS Upload Fix, Stored Procedure RPC,
--           RLS Security Hardening & High-Performance SQL Aggregation
-- Target: Supabase PostgreSQL 15+
-- ============================================================================

-- Ensure uuid-ossp extension is enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- 1. SECURE SERVER-SIDE SOS INGESTION RPC (SECURITY DEFINER)
-- ============================================================================
-- This function runs with SECURITY DEFINER privileges (owner context), allowing
-- anonymous citizen distress beacons and authenticated users to submit emergency
-- alerts without triggering PostgreSQL RLS 42501 errors on INSERT RETURNING *.
-- Android clients NEVER hold the Supabase service_role key.
-- All validation, deduplication, and rate limiting are enforced server-side.

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
    v_latitude DOUBLE PRECISION := NULL;
    v_longitude DOUBLE PRECISION := NULL;
    v_accuracy DOUBLE PRECISION := NULL;
    v_location_timestamp TIMESTAMPTZ := NULL;
    v_created_at TIMESTAMPTZ := NULL;
    v_expires_at TIMESTAMPTZ := NULL;
    v_hop_count INTEGER := 0;
    v_protocol_version INTEGER := 1;
    v_payload_hash TEXT;
    v_existing_id UUID;
    v_new_id UUID;
    v_recent_alert_count INTEGER := 0;
    v_user_id UUID;
BEGIN
    -- 1. Protocol Version Validation
    v_protocol_version := COALESCE(
        (payload->>'protocolVersion')::INTEGER,
        (payload->>'protocol_version')::INTEGER,
        1
    );
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

    -- 7. Coordinates Validation (GPS may be unavailable offline)
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
    BEGIN
        SELECT COUNT(*) INTO v_recent_alert_count
        FROM public.gateway_upload_history
        WHERE gateway_device_id = v_gateway_device
          AND upload_attempted_at > timezone('utc'::text, now()) - INTERVAL '1 hour';

        IF v_recent_alert_count > 60 THEN
            RAISE EXCEPTION 'RATE_LIMIT_EXCEEDED: Gateway % exceeded upload limit (60 alerts/hr)', v_gateway_device
                USING ERRCODE = '22000';
        END IF;
    EXCEPTION
        WHEN undefined_table THEN
            NULL; -- gateway_upload_history table does not exist yet; non-blocking
    END;

    -- 10. Check Idempotency & Duplicate Handling
    SELECT id INTO v_existing_id
    FROM public.emergency_alerts
    WHERE message_id = v_message_id;

    IF v_existing_id IS NOT NULL THEN
        -- Log duplicate gateway encounter
        BEGIN
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
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;

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
    BEGIN
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
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

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
        NULL;
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

-- Grant execution to anon (unauthenticated distress beacons) and authenticated users
GRANT EXECUTE ON FUNCTION public.submit_emergency_alert(jsonb) TO anon, authenticated, service_role;

-- ============================================================================
-- 2. HARDEN RLS POLICIES FOR EMERGENCY_ALERTS TABLE
-- ============================================================================
ALTER TABLE public.emergency_alerts ENABLE ROW LEVEL SECURITY;

-- 2.1 Allow both anonymous beacons and authenticated users to INSERT
DROP POLICY IF EXISTS "Allow public insert of emergency alerts" ON public.emergency_alerts;
DROP POLICY IF EXISTS "Allow anon and auth insert of emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Allow anon and auth insert of emergency alerts"
ON public.emergency_alerts
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 2.2 Authorized operational personnel can view all alerts
DROP POLICY IF EXISTS "Authorized personnel can view all emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Authorized personnel can view all emergency alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN (
        'SUPER_ADMIN',
        'CONTROL_ROOM_OPERATOR',
        'FIELD_RESPONSE_COORDINATOR',
        'PUBLIC_WARNING_PUBLISHER',
        'VIEWER'
    )
);

-- 2.3 Registered citizens can view alerts originating from devices they own
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

-- ============================================================================
-- 3. HIGH-PERFORMANCE SQL AGGREGATION FUNCTIONS (ELIMINATES CLIENT MONOLITH)
-- ============================================================================
-- Instead of downloading 10,000+ full incident records to compute dashboard counts
-- in browser JavaScript, these functions perform aggregation directly in Postgres.

CREATE OR REPLACE FUNCTION public.get_incident_stats_summary()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT jsonb_build_object(
        'totalReceived', COUNT(*),
        'awaitingVerification', COUNT(*) FILTER (WHERE verification_status = 'UNVERIFIED'),
        'verifiedActive', COUNT(*) FILTER (WHERE verification_status = 'VERIFIED'),
        'assignedCount', COUNT(*) FILTER (WHERE assigned_team_id IS NOT NULL),
        'resolvedCount', COUNT(*) FILTER (WHERE operator_status IN ('RESOLVED', 'CLOSED')),
        'expiredCount', COUNT(*) FILTER (WHERE expires_at < timezone('utc'::text, now()))
    )
    FROM public.emergency_alerts;
$$;

GRANT EXECUTE ON FUNCTION public.get_incident_stats_summary() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_operational_analytics_summary(
    p_date_from TIMESTAMPTZ DEFAULT NULL,
    p_date_to TIMESTAMPTZ DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_total INTEGER := 0;
    v_with_gps INTEGER := 0;
    v_cat_dist jsonb;
    v_prio_dist jsonb;
    v_status_dist jsonb;
    v_verif_dist jsonb;
    v_hop_dist jsonb;
    v_daily_trend jsonb;
BEGIN
    -- Total count & GPS lock count
    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND NOT (latitude = 0 AND longitude = 0))
    INTO v_total, v_with_gps
    FROM public.emergency_alerts
    WHERE (p_date_from IS NULL OR created_at >= p_date_from)
      AND (p_date_to IS NULL OR created_at <= p_date_to);

    -- Category distribution
    SELECT COALESCE(jsonb_object_agg(category, cnt), '{}'::jsonb)
    INTO v_cat_dist
    FROM (
        SELECT category, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY category
    ) c;

    -- Priority distribution
    SELECT COALESCE(jsonb_object_agg(priority::text, cnt), '{}'::jsonb)
    INTO v_prio_dist
    FROM (
        SELECT priority, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY priority
    ) p;

    -- Operator Status distribution
    SELECT COALESCE(jsonb_object_agg(operator_status::text, cnt), '{}'::jsonb)
    INTO v_status_dist
    FROM (
        SELECT operator_status, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY operator_status
    ) s;

    -- Verification Status distribution
    SELECT COALESCE(jsonb_object_agg(verification_status, cnt), '{}'::jsonb)
    INTO v_verif_dist
    FROM (
        SELECT verification_status, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY verification_status
    ) v;

    -- Hop count distribution
    SELECT COALESCE(jsonb_object_agg(hop_count::text, cnt), '{}'::jsonb)
    INTO v_hop_dist
    FROM (
        SELECT hop_count, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY hop_count
    ) h;

    -- Daily Trend (last 30 days)
    SELECT COALESCE(jsonb_agg(jsonb_build_object('date', day_str, 'count', cnt) ORDER BY day_str), '[]'::jsonb)
    INTO v_daily_trend
    FROM (
        SELECT to_char(created_at, 'YYYY-MM-DD') as day_str, COUNT(*) as cnt
        FROM public.emergency_alerts
        WHERE (p_date_from IS NULL OR created_at >= p_date_from)
          AND (p_date_to IS NULL OR created_at <= p_date_to)
        GROUP BY day_str
    ) d;

    RETURN jsonb_build_object(
        'totalAlerts', v_total,
        'categoryDistribution', v_cat_dist,
        'priorityDistribution', v_prio_dist,
        'statusDistribution', v_status_dist,
        'verificationDistribution', v_verif_dist,
        'hopCountDistribution', v_hop_dist,
        'locationFixStats', jsonb_build_object(
            'withGps', v_with_gps,
            'withoutGps', v_total - v_with_gps,
            'percentageWithGps', CASE WHEN v_total > 0 THEN ROUND((v_with_gps::numeric / v_total::numeric) * 100) ELSE 0 END
        ),
        'dailyTrend', v_daily_trend
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_operational_analytics_summary(TIMESTAMPTZ, TIMESTAMPTZ) TO anon, authenticated, service_role;

-- ============================================================================
-- 4. COMPOSITE PERFORMANCE INDEXES
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_created_desc
    ON public.emergency_alerts (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_priority_created
    ON public.emergency_alerts (priority, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_verification_status
    ON public.emergency_alerts (verification_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_geo
    ON public.emergency_alerts (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_emergency_alerts_assigned_team_active
    ON public.emergency_alerts (assigned_team_id)
    WHERE assigned_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_gateway_history_lookup
    ON public.gateway_upload_history (gateway_device_id, upload_attempted_at DESC);

