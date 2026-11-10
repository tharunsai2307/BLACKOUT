-- ============================================================================
-- BLACKOUT Emergency Management Platform
-- Phase 19: Fix publish_and_sign_warning Stored Procedure to include effective_at and expires_at
-- ============================================================================

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
    v_expires timestamptz;
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
        revision = COALESCE(revision, 1) + 1
    WHERE id = p_warning_id
    RETURNING * INTO v_warning;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'WARNING_NOT_FOUND: Warning % does not exist', p_warning_id USING ERRCODE = 'P0002';
    END IF;

    v_expires := COALESCE(v_warning.expires_at, v_now + interval '24 hours');

    -- Insert immutable revision record with all non-null columns
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
        issued_at,
        effective_at,
        expires_at,
        created_at
    ) VALUES (
        v_warning.id,
        v_warning.warning_code,
        v_warning.revision,
        v_warning.title,
        v_warning.description,
        v_warning.category,
        v_warning.severity,
        'PUBLISHED',
        COALESCE(v_warning.issuing_authority, 'BLACKOUT Civil Defense & Emergency Response Administration'),
        v_warning.signing_key_id,
        v_warning.signature_algorithm,
        v_warning.signature,
        v_warning.canonical_payload,
        COALESCE(v_warning.affected_regions, '["General District"]'::jsonb),
        v_warning.latitude,
        v_warning.longitude,
        COALESCE(v_warning.radius_meters, CASE WHEN v_warning.radius_km IS NOT NULL THEN v_warning.radius_km * 1000 ELSE NULL END),
        COALESCE(v_warning.language, 'en'),
        v_now,
        v_now,
        v_expires,
        v_now
    );

    RETURN to_jsonb(v_warning);
END;
$$;

GRANT EXECUTE ON FUNCTION public.publish_and_sign_warning(uuid, text, text, text) TO anon, authenticated, service_role;
