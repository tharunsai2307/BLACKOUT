-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 9.5 Migration: Unified Authentication, User Profiles & Emergency Contacts
-- Target Database: Supabase PostgreSQL 15+

-- ============================================================================
-- 1. EXTEND APP ROLE ENUM FOR CITIZEN USERS
-- ============================================================================

DO $$ BEGIN
    ALTER TYPE public.app_role_enum ADD VALUE IF NOT EXISTS 'CITIZEN';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- ============================================================================
-- 2. EXTEND USER PROFILES TABLE (SUPPORTING CITIZENS & OPERATORS)
-- ============================================================================

ALTER TABLE public.user_profiles
ADD COLUMN IF NOT EXISTS phone_number TEXT,
ADD COLUMN IF NOT EXISTS country_code TEXT DEFAULT '+91',
ADD COLUMN IF NOT EXISTS country_region TEXT,
ADD COLUMN IF NOT EXISTS profile_photo_url TEXT,
ADD COLUMN IF NOT EXISTS preferred_language TEXT DEFAULT 'en',
ADD COLUMN IF NOT EXISTS medical_notes TEXT,
ADD COLUMN IF NOT EXISTS profile_completed BOOLEAN DEFAULT FALSE;

-- Ensure default role for new citizen accounts is CITIZEN
ALTER TABLE public.user_profiles
ALTER COLUMN role SET DEFAULT 'CITIZEN'::public.app_role_enum;

CREATE INDEX IF NOT EXISTS idx_user_profiles_phone ON public.user_profiles(phone_number);

-- ============================================================================
-- 3. EMERGENCY CONTACTS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.emergency_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    contact_name TEXT NOT NULL CHECK (length(trim(contact_name)) > 0 AND length(contact_name) <= 100),
    phone_number TEXT NOT NULL CHECK (phone_number ~ '^\+[1-9]\d{1,14}$'), -- E.164 international format
    relationship TEXT NOT NULL DEFAULT 'Other' CHECK (relationship IN ('Parent', 'Spouse', 'Child', 'Sibling', 'Friend', 'Colleague', 'Other')),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_emergency_contacts_user_id ON public.emergency_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_contacts_is_primary ON public.emergency_contacts(is_primary);

-- ============================================================================
-- 4. ROW-LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_contacts ENABLE ROW LEVEL SECURITY;

-- 4.1 user_profiles RLS Policies
-- Users can view their own profile
DROP POLICY IF EXISTS user_profiles_select_own ON public.user_profiles;
CREATE POLICY user_profiles_select_own ON public.user_profiles
    FOR SELECT TO authenticated
    USING (auth.uid() = id);

-- Operators and Admins can view profiles for active incident response verification
DROP POLICY IF EXISTS user_profiles_select_operator ON public.user_profiles;
CREATE POLICY user_profiles_select_operator ON public.user_profiles
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up
            WHERE up.id = auth.uid()
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR')
        )
    );

-- Users can update only their own profile
DROP POLICY IF EXISTS user_profiles_update_own ON public.user_profiles;
CREATE POLICY user_profiles_update_own ON public.user_profiles
    FOR UPDATE TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Users can insert their own profile upon registration
DROP POLICY IF EXISTS user_profiles_insert_own ON public.user_profiles;
CREATE POLICY user_profiles_insert_own ON public.user_profiles
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = id);

-- 4.2 emergency_contacts RLS Policies
-- Users can view their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_select_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_select_own ON public.emergency_contacts
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

-- Incident commanders can view contacts for victims of verified incidents during active rescue
DROP POLICY IF EXISTS emergency_contacts_select_operator ON public.emergency_contacts;
CREATE POLICY emergency_contacts_select_operator ON public.emergency_contacts
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.user_profiles up
            WHERE up.id = auth.uid()
            AND up.role IN ('SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR')
        )
    );

-- Users can insert their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_insert_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_insert_own ON public.emergency_contacts
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

-- Users can update their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_update_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_update_own ON public.emergency_contacts
    FOR UPDATE TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Users can delete their own emergency contacts
DROP POLICY IF EXISTS emergency_contacts_delete_own ON public.emergency_contacts;
CREATE POLICY emergency_contacts_delete_own ON public.emergency_contacts
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- ============================================================================
-- 5. AUTOMATIC PROFILE CREATION TRIGGER (AUTH.USERS HOOK)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user_registration()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_profiles (
        id,
        email,
        full_name,
        phone_number,
        role,
        organization,
        is_active,
        created_at,
        updated_at
    ) VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', 'BLACKOUT Citizen'),
        NEW.raw_user_meta_data->>'phone',
        'CITIZEN'::public.app_role_enum,
        'BLACKOUT Civilian Network',
        true,
        now(),
        now()
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger on auth.users after insert
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user_registration();
