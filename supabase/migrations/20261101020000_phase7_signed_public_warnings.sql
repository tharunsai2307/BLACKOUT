-- BLACKOUT Decentralized Emergency Communications Platform
-- Phase 7 Digitally Signed Public Warnings & Key Registry Migration
-- Target Database: Supabase PostgreSQL

-- 1. Create Warning Signing Keys Registry Table
CREATE TABLE IF NOT EXISTS public.warning_signing_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_id TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL, -- Raw 32-byte Ed25519 public key encoded in Base64
    algorithm TEXT NOT NULL DEFAULT 'Ed25519',
    authority_id TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    key_status TEXT NOT NULL CHECK (key_status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_signing_keys_status ON public.warning_signing_keys(key_status);
CREATE INDEX IF NOT EXISTS idx_signing_keys_key_id ON public.warning_signing_keys(key_id);

-- 2. Extend public_warnings Table with Cryptographic and Protocol Fields
ALTER TABLE public.public_warnings
ADD COLUMN IF NOT EXISTS protocol_version INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS signing_key_id TEXT REFERENCES public.warning_signing_keys(key_id) ON DELETE RESTRICT,
ADD COLUMN IF NOT EXISTS signature_algorithm TEXT NOT NULL DEFAULT 'Ed25519',
ADD COLUMN IF NOT EXISTS signature TEXT, -- Base64 64-byte Ed25519 digital signature
ADD COLUMN IF NOT EXISTS canonical_payload TEXT, -- Deterministic signed representation
ADD COLUMN IF NOT EXISTS affected_regions JSONB NOT NULL DEFAULT '[]'::jsonb,
ADD COLUMN IF NOT EXISTS radius_meters DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'en',
ADD COLUMN IF NOT EXISTS effective_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now());

CREATE INDEX IF NOT EXISTS idx_public_warnings_key_id ON public.public_warnings(signing_key_id);
CREATE INDEX IF NOT EXISTS idx_public_warnings_revision ON public.public_warnings(warning_code, revision);

-- 3. Create Warning Revisions Table (Immutable Revision History)
CREATE TABLE IF NOT EXISTS public.warning_revisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warning_id UUID NOT NULL REFERENCES public.public_warnings(id) ON DELETE CASCADE,
    warning_code TEXT NOT NULL,
    revision INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL,
    severity TEXT NOT NULL,
    status TEXT NOT NULL,
    issuing_authority TEXT NOT NULL,
    signing_key_id TEXT NOT NULL REFERENCES public.warning_signing_keys(key_id) ON DELETE RESTRICT,
    signature_algorithm TEXT NOT NULL DEFAULT 'Ed25519',
    signature TEXT NOT NULL,
    canonical_payload TEXT NOT NULL,
    affected_regions JSONB NOT NULL DEFAULT '[]'::jsonb,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    radius_meters DOUBLE PRECISION,
    language TEXT NOT NULL DEFAULT 'en',
    issued_at TIMESTAMPTZ NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_warning_revision UNIQUE (warning_id, revision)
);

CREATE INDEX IF NOT EXISTS idx_warning_revisions_warning_id ON public.warning_revisions(warning_id);
CREATE INDEX IF NOT EXISTS idx_warning_revisions_code_rev ON public.warning_revisions(warning_code, revision);

-- 4. Create Warning Revocations Table
CREATE TABLE IF NOT EXISTS public.warning_revocations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warning_id UUID NOT NULL REFERENCES public.public_warnings(id) ON DELETE CASCADE,
    warning_code TEXT NOT NULL,
    revision INTEGER NOT NULL,
    revoked_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    revocation_reason TEXT NOT NULL,
    revocation_signature TEXT,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_warning_revocations_warning_id ON public.warning_revocations(warning_id);

-- 5. Enable Row Level Security (RLS)
ALTER TABLE public.warning_signing_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.warning_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.warning_revocations ENABLE ROW LEVEL SECURITY;

-- 6. RLS Policies: warning_signing_keys
-- Public & Mobile Clients can read all public keys for offline verification
CREATE POLICY "Public and Android app can read signing keys"
ON public.warning_signing_keys
FOR SELECT
TO anon, authenticated
USING (true);

-- Only Super Admins can insert/update signing keys
CREATE POLICY "Super Admins can manage signing keys"
ON public.warning_signing_keys
FOR ALL
TO authenticated
USING (public.get_current_user_role() = 'SUPER_ADMIN')
WITH CHECK (public.get_current_user_role() = 'SUPER_ADMIN');

CREATE POLICY "Service role full access on warning_signing_keys"
ON public.warning_signing_keys
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 7. RLS Policies: warning_revisions
CREATE POLICY "Public and Android app can read published revisions"
ON public.warning_revisions
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Publishers and Admins can create revisions"
ON public.warning_revisions
FOR INSERT
TO authenticated
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER'));

CREATE POLICY "Service role full access on warning_revisions"
ON public.warning_revisions
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 8. RLS Policies: warning_revocations
CREATE POLICY "Public and Android app can read revocations"
ON public.warning_revocations
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Publishers and Admins can create revocations"
ON public.warning_revocations
FOR INSERT
TO authenticated
WITH CHECK (public.get_current_user_role() IN ('SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER'));

CREATE POLICY "Service role full access on warning_revocations"
ON public.warning_revocations
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- 9. Realtime Replication for Warnings & Keys
DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_signing_keys;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_revisions;
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.warning_revocations;
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- 10. Seed Initial Root Verification Key
-- Key: KEY-BLACKOUT-ROOT-2026 (RFC 8032 Ed25519)
-- Authority: BLACKOUT Civil Defense & Emergency Response Administration
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
    '2036-01-01T00:00:00Z',
    'ACTIVE'
) ON CONFLICT (key_id) DO NOTHING;
