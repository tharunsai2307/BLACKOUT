-- ============================================================================
-- BLACKOUT Emergency Management Platform
-- Phase 14: Fix Response Team & Public Warning Permissions & RPC Ingestion
-- ============================================================================

-- 1. Ensure RLS allows operators to insert response teams
DROP POLICY IF EXISTS "Operators and Admins can manage response teams" ON public.response_teams;
CREATE POLICY "Operators and Admins can manage response teams"
ON public.response_teams
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS "Authorized staff can view response teams" ON public.response_teams;
CREATE POLICY "Authorized staff can view response teams"
ON public.response_teams
FOR SELECT
TO anon, authenticated
USING (true);

-- 2. Ensure RLS allows operators to insert public warnings
DROP POLICY IF EXISTS "Publishers and Admins can manage warnings" ON public.public_warnings;
CREATE POLICY "Publishers and Admins can manage warnings"
ON public.public_warnings
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS "Authorized personnel can view all warnings" ON public.public_warnings;
CREATE POLICY "Authorized personnel can view all warnings"
ON public.public_warnings
FOR SELECT
TO anon, authenticated
USING (true);

-- 3. Stored Procedure RPC: Create Response Team (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.create_response_team(
    p_name text,
    p_callsign text,
    p_team_type text,
    p_contact_phone text DEFAULT NULL,
    p_base_location text DEFAULT NULL,
    p_capacity integer DEFAULT 4
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_new_team public.response_teams;
    v_type public.team_type_enum;
BEGIN
    BEGIN
        v_type := UPPER(TRIM(p_team_type))::public.team_type_enum;
    EXCEPTION WHEN OTHERS THEN
        v_type := 'GENERAL'::public.team_type_enum;
    END;

    INSERT INTO public.response_teams (
        name,
        callsign,
        team_type,
        status,
        contact_phone,
        base_location,
        capacity
    ) VALUES (
        TRIM(p_name),
        UPPER(TRIM(p_callsign)),
        v_type,
        'AVAILABLE',
        NULLIF(TRIM(p_contact_phone), ''),
        NULLIF(TRIM(p_base_location), ''),
        COALESCE(p_capacity, 4)
    )
    RETURNING * INTO v_new_team;

    RETURN to_jsonb(v_new_team);
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_response_team(text, text, text, text, text, integer) TO anon, authenticated, service_role;

-- 4. Stored Procedure RPC: Create Public Warning (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.create_public_warning(
    p_title text,
    p_description text,
    p_category text,
    p_severity text,
    p_affected_area text,
    p_instructions text DEFAULT NULL,
    p_latitude double precision DEFAULT NULL,
    p_longitude double precision DEFAULT NULL,
    p_radius_km double precision DEFAULT NULL,
    p_expires_at timestamptz DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_new_warning public.public_warnings;
    v_warning_code text;
    v_sev public.warning_severity_enum;
    v_expires timestamptz;
BEGIN
    v_warning_code := 'WARN-' || to_char(now(), 'YYYY') || '-' || floor(1000 + random() * 9000)::text;

    BEGIN
        v_sev := UPPER(TRIM(p_severity))::public.warning_severity_enum;
    EXCEPTION WHEN OTHERS THEN
        v_sev := 'MODERATE'::public.warning_severity_enum;
    END;

    v_expires := COALESCE(p_expires_at, now() + interval '24 hours');

    INSERT INTO public.public_warnings (
        warning_code,
        title,
        description,
        category,
        severity,
        affected_area,
        instructions,
        latitude,
        longitude,
        radius_km,
        status,
        expires_at
    ) VALUES (
        v_warning_code,
        TRIM(p_title),
        TRIM(p_description),
        TRIM(p_category),
        v_sev,
        TRIM(p_affected_area),
        NULLIF(TRIM(p_instructions), ''),
        p_latitude,
        p_longitude,
        p_radius_km,
        'DRAFT',
        v_expires
    )
    RETURNING * INTO v_new_warning;

    RETURN to_jsonb(v_new_warning);
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_public_warning(text, text, text, text, text, text, double precision, double precision, double precision, timestamptz) TO anon, authenticated, service_role;

