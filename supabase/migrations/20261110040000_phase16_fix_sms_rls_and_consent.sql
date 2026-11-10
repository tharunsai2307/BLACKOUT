-- ============================================================================
-- BLACKOUT Emergency Management Platform
-- Phase 16: Fix SMS System RLS Policies and Recipient Consent Stored Procedure
-- ============================================================================

-- 1. Ensure RLS on sms_recipient_preferences allows reading and logging consent
DROP POLICY IF EXISTS sms_recipient_preferences_select ON public.sms_recipient_preferences;
DROP POLICY IF EXISTS sms_recipient_preferences_insert_update ON public.sms_recipient_preferences;
DROP POLICY IF EXISTS "Allow read access to sms_recipient_preferences" ON public.sms_recipient_preferences;
DROP POLICY IF EXISTS "Allow write access to sms_recipient_preferences" ON public.sms_recipient_preferences;

CREATE POLICY "Allow read access to sms_recipient_preferences"
ON public.sms_recipient_preferences
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to sms_recipient_preferences"
ON public.sms_recipient_preferences
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 2. sms_notification_requests policies
DROP POLICY IF EXISTS sms_notification_requests_select ON public.sms_notification_requests;
DROP POLICY IF EXISTS sms_notification_requests_insert ON public.sms_notification_requests;
DROP POLICY IF EXISTS "Allow read access to sms_notification_requests" ON public.sms_notification_requests;
DROP POLICY IF EXISTS "Allow write access to sms_notification_requests" ON public.sms_notification_requests;

CREATE POLICY "Allow read access to sms_notification_requests"
ON public.sms_notification_requests
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to sms_notification_requests"
ON public.sms_notification_requests
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 3. sms_delivery_attempts policies
DROP POLICY IF EXISTS sms_delivery_attempts_select ON public.sms_delivery_attempts;
DROP POLICY IF EXISTS sms_delivery_attempts_insert ON public.sms_delivery_attempts;
DROP POLICY IF EXISTS "Allow read access to sms_delivery_attempts" ON public.sms_delivery_attempts;
DROP POLICY IF EXISTS "Allow write access to sms_delivery_attempts" ON public.sms_delivery_attempts;

CREATE POLICY "Allow read access to sms_delivery_attempts"
ON public.sms_delivery_attempts
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to sms_delivery_attempts"
ON public.sms_delivery_attempts
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 4. Stored Procedure RPC: Record Recipient Consent (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.record_recipient_consent(
    p_phone_number text,
    p_has_emergency_consent boolean DEFAULT true,
    p_has_warning_consent boolean DEFAULT false,
    p_source text DEFAULT 'OPERATOR_VERIFIED'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_record public.sms_recipient_preferences;
    v_now timestamptz := timezone('utc'::text, now());
BEGIN
    INSERT INTO public.sms_recipient_preferences (
        phone_number,
        has_emergency_contact_consent,
        has_public_warning_consent,
        verification_status,
        consent_source,
        consent_timestamp,
        updated_at
    ) VALUES (
        TRIM(p_phone_number),
        COALESCE(p_has_emergency_consent, true),
        COALESCE(p_has_warning_consent, false),
        'VERIFIED',
        COALESCE(p_source, 'OPERATOR_VERIFIED'),
        v_now,
        v_now
    )
    ON CONFLICT (phone_number) DO UPDATE SET
        has_emergency_contact_consent = EXCLUDED.has_emergency_contact_consent,
        has_public_warning_consent = EXCLUDED.has_public_warning_consent,
        verification_status = 'VERIFIED',
        consent_source = EXCLUDED.consent_source,
        consent_timestamp = v_now,
        updated_at = v_now
    RETURNING * INTO v_record;

    RETURN to_jsonb(v_record);
END;
$$;

GRANT EXECUTE ON FUNCTION public.record_recipient_consent(text, boolean, boolean, text) TO anon, authenticated, service_role;

