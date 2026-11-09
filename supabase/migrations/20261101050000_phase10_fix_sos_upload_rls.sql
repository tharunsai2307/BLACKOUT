-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 10 Migration: Secure Server-Side SOS Ingestion RPC & RLS Hardening
-- Target Database: Supabase PostgreSQL 15+

-- ============================================================================
-- 1. SECURE SERVER-SIDE SOS SUBMISSION RPC (SECURITY DEFINER)
-- ============================================================================
-- Allows mobile gateways (both authenticated citizens and anonymous guest distress beacons)
-- to reliably submit validated emergency alerts without RLS evaluation conflicts.
-- Android clients NEVER hold service_role keys. This function executes with elevated
-- database rights, enforcing strict server-side validation, rate limiting, and idempotency.

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
        -- Non-blocking for event logging
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

-- Grant execution to anon (guests) and authenticated users (citizens & operators)
GRANT EXECUTE ON FUNCTION public.submit_emergency_alert(jsonb) TO anon, authenticated, service_role;

-- ============================================================================
-- 2. HARDEN RLS POLICIES FOR EMERGENCY_ALERTS TABLE
-- ============================================================================

-- Ensure RLS is active
ALTER TABLE public.emergency_alerts ENABLE ROW LEVEL SECURITY;

-- 2.1 INSERT Policy: Allow both anonymous guest distress beacons and authenticated users to insert
DROP POLICY IF EXISTS "Allow public insert of emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Allow public insert of emergency alerts"
ON public.emergency_alerts
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 2.2 SELECT Policy:
-- Authorized personnel can view all alerts for emergency coordination
DROP POLICY IF EXISTS "Authorized personnel can view all emergency alerts" ON public.emergency_alerts;
CREATE POLICY "Authorized personnel can view all emergency alerts"
ON public.emergency_alerts
FOR SELECT
TO authenticated
USING (
    public.get_current_user_role() IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR', 'PUBLIC_WARNING_PUBLISHER', 'VIEWER')
);

-- Registered citizens can view alerts originating from devices they own
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

-- Gateways (both authenticated and anon) can check if a specific alert message_id exists
-- via a secure check helper function without exposing sensitive incident descriptions
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

