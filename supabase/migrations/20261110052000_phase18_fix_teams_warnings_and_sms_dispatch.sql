-- ============================================================================
-- BLACKOUT Emergency Management Platform
-- Phase 18: Fix Response Teams, Public Warnings RLS for Command Center & SMS Dispatch
-- ============================================================================

-- 1. Response Teams Policies for Operators (anon and authenticated)
DROP POLICY IF EXISTS "Allow read access to response_teams" ON public.response_teams;
DROP POLICY IF EXISTS "Allow write access to response_teams" ON public.response_teams;

CREATE POLICY "Allow read access to response_teams"
ON public.response_teams
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to response_teams"
ON public.response_teams
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 2. Response Team Members Policies
DROP POLICY IF EXISTS "Allow read access to response_team_members" ON public.response_team_members;
DROP POLICY IF EXISTS "Allow write access to response_team_members" ON public.response_team_members;

CREATE POLICY "Allow read access to response_team_members"
ON public.response_team_members
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to response_team_members"
ON public.response_team_members
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 3. Public Warnings Policies for Operators and Citizens
DROP POLICY IF EXISTS "Allow read access to public_warnings" ON public.public_warnings;
DROP POLICY IF EXISTS "Allow write access to public_warnings" ON public.public_warnings;

CREATE POLICY "Allow read access to public_warnings"
ON public.public_warnings
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to public_warnings"
ON public.public_warnings
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 4. Warning Revisions and Revocations Policies
DROP POLICY IF EXISTS "Allow read access to warning_revisions" ON public.warning_revisions;
DROP POLICY IF EXISTS "Allow write access to warning_revisions" ON public.warning_revisions;

CREATE POLICY "Allow read access to warning_revisions"
ON public.warning_revisions
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to warning_revisions"
ON public.warning_revisions
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS "Allow read access to warning_revocations" ON public.warning_revocations;
DROP POLICY IF EXISTS "Allow write access to warning_revocations" ON public.warning_revocations;

CREATE POLICY "Allow read access to warning_revocations"
ON public.warning_revocations
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to warning_revocations"
ON public.warning_revocations
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 5. Audit Logs Policies
DROP POLICY IF EXISTS "Allow read access to audit_logs" ON public.audit_logs;
DROP POLICY IF EXISTS "Allow write access to audit_logs" ON public.audit_logs;

CREATE POLICY "Allow read access to audit_logs"
ON public.audit_logs
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow write access to audit_logs"
ON public.audit_logs
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 6. Ensure SMS preferences and requests have full permissions
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

-- 7. Grant RPC execute permissions to anon and authenticated
GRANT EXECUTE ON FUNCTION public.create_public_warning(text, text, text, text, text, text, double precision, double precision, double precision, timestamptz) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.publish_and_sign_warning(uuid, text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_recipient_consent(text, boolean, boolean, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.dispatch_emergency_sms_rpc(text, text, text, jsonb, uuid, uuid, text, text) TO anon, authenticated, service_role;
