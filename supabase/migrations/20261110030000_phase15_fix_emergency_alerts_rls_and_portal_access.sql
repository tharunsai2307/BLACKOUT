-- ============================================================================
-- BLACKOUT Emergency Management Platform
-- Phase 15: Fix Emergency Alerts RLS, Portal Operations Access, and Key Registry
-- ============================================================================

-- 1. Ensure RLS on emergency_alerts allows command portal reading and updates
DROP POLICY IF EXISTS "Authorized personnel can view all emergency alerts" ON public.emergency_alerts;
DROP POLICY IF EXISTS "Allow read access to emergency_alerts" ON public.emergency_alerts;
CREATE POLICY "Allow read access to emergency_alerts"
ON public.emergency_alerts
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow update access to emergency_alerts" ON public.emergency_alerts;
CREATE POLICY "Allow update access to emergency_alerts"
ON public.emergency_alerts
FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 2. Audit and history read policies
DROP POLICY IF EXISTS "Allow read access to emergency_alert_events" ON public.emergency_alert_events;
CREATE POLICY "Allow read access to emergency_alert_events"
ON public.emergency_alert_events
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow read access to gateway_upload_history" ON public.gateway_upload_history;
CREATE POLICY "Allow read access to gateway_upload_history"
ON public.gateway_upload_history
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow read access to warning_revisions" ON public.warning_revisions;
CREATE POLICY "Allow read access to warning_revisions"
ON public.warning_revisions
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow read access to warning_signing_keys" ON public.warning_signing_keys;
CREATE POLICY "Allow read access to warning_signing_keys"
ON public.warning_signing_keys
FOR SELECT
TO anon, authenticated
USING (true);

-- 3. Stored Procedure RPC for updating incident status (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.update_incident_status(
    p_alert_id uuid,
    p_operator_status text DEFAULT NULL,
    p_verification_status text DEFAULT NULL,
    p_assigned_team_id uuid DEFAULT NULL,
    p_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_alert public.emergency_alerts;
    v_op_status public.operator_status_enum;
    v_ver_status text;
BEGIN
    SELECT * INTO v_alert FROM public.emergency_alerts WHERE id = p_alert_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'ALERT_NOT_FOUND: Alert % does not exist', p_alert_id USING ERRCODE = 'P0002';
    END IF;

    IF p_operator_status IS NOT NULL AND TRIM(p_operator_status) != '' THEN
        BEGIN
            v_op_status := UPPER(TRIM(p_operator_status))::public.operator_status_enum;
        EXCEPTION WHEN OTHERS THEN
            v_op_status := v_alert.operator_status;
        END;
    ELSE
        v_op_status := v_alert.operator_status;
    END IF;

    IF p_verification_status IS NOT NULL AND TRIM(p_verification_status) != '' THEN
        v_ver_status := UPPER(TRIM(p_verification_status));
    ELSE
        v_ver_status := v_alert.verification_status;
    END IF;

    UPDATE public.emergency_alerts
    SET
        operator_status = v_op_status,
        verification_status = v_ver_status,
        assigned_team_id = CASE
            WHEN p_assigned_team_id IS NOT NULL THEN p_assigned_team_id
            ELSE assigned_team_id
        END,
        operator_notes = CASE
            WHEN p_notes IS NOT NULL THEN p_notes
            ELSE operator_notes
        END,
        updated_at = timezone('utc'::text, now())
    WHERE id = p_alert_id
    RETURNING * INTO v_alert;

    -- Record event in audit trail
    INSERT INTO public.emergency_alert_events (
        message_id,
        actor_id,
        event_type,
        metadata
    ) VALUES (
        v_alert.message_id,
        'command-center-operator',
        'STATUS_UPDATED',
        jsonb_build_object(
            'operator_status', v_alert.operator_status,
            'verification_status', v_alert.verification_status,
            'assigned_team_id', v_alert.assigned_team_id,
            'operator_notes', v_alert.operator_notes
        )
    );

    RETURN to_jsonb(v_alert);
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_incident_status(uuid, text, text, uuid, text) TO anon, authenticated, service_role;

-- 4. Ensure Root Signing Key is present in warning_signing_keys
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
    '2030-01-01T00:00:00Z',
    'ACTIVE'
) ON CONFLICT (key_id) DO UPDATE SET
    public_key = EXCLUDED.public_key,
    key_status = 'ACTIVE';

-- 5. Stored Procedure RPC: Publish and Sign Warning directly via DB (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.publish_and_sign_warning(
    p_warning_id uuid,
    p_signature text,
    p_canonical_payload text,
    p_signing_key_id text DEFAULT 'KEY-BLACKOUT-ROOT-2026'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_warning public.public_warnings;
    v_now timestamptz := timezone('utc'::text, now());
BEGIN
    UPDATE public.public_warnings
    SET
        status = 'PUBLISHED',
        signature = p_signature,
        canonical_payload = p_canonical_payload,
        signing_key_id = p_signing_key_id,
        signature_algorithm = 'Ed25519',
        published_at = v_now,
        updated_at = v_now,
        revision = revision + 1
    WHERE id = p_warning_id
    RETURNING * INTO v_warning;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'WARNING_NOT_FOUND: Warning % does not exist', p_warning_id USING ERRCODE = 'P0002';
    END IF;

    -- Insert immutable revision record
    INSERT INTO public.warning_revisions (
        warning_id,
        warning_code,
        revision,
        title,
        description,
        category,
        severity,
        status,
        issuing_authority,
        signing_key_id,
        signature_algorithm,
        signature,
        canonical_payload,
        affected_regions,
        latitude,
        longitude,
        radius_meters,
        language,
        issued_at
    ) VALUES (
        v_warning.id,
        v_warning.warning_code,
        v_warning.revision,
        v_warning.title,
        v_warning.description,
        v_warning.category,
        v_warning.severity,
        'PUBLISHED',
        v_warning.issuing_authority,
        v_warning.signing_key_id,
        v_warning.signature_algorithm,
        v_warning.signature,
        v_warning.canonical_payload,
        COALESCE(v_warning.affected_regions, '[]'::jsonb),
        v_warning.latitude,
        v_warning.longitude,
        v_warning.radius_meters,
        COALESCE(v_warning.language, 'en'),
        v_now
    );

    RETURN to_jsonb(v_warning);
END;
$$;

GRANT EXECUTE ON FUNCTION public.publish_and_sign_warning(uuid, text, text, text) TO anon, authenticated, service_role;
