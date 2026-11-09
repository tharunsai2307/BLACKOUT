-- BLACKOUT Operations Portal — Phase 12 Seeding: Tactical Response Teams & Command Center Profiles

-- 1. Ensure Tactical Response Teams are seeded in public.response_teams
INSERT INTO public.response_teams (name, callsign, team_type, status, contact_phone, base_location, capacity)
VALUES
    ('Alpha Rapid Search & Rescue', 'SAR-ALPHA', 'SEARCH_AND_RESCUE', 'AVAILABLE', '+91-98765-01001', 'Central Tactical Staging Ground', 6),
    ('Bravo Emergency Medical Corps', 'MED-BRAVO', 'MEDICAL_RESPONSE', 'AVAILABLE', '+91-98765-01002', 'City Civil Hospital Trauma Unit', 4),
    ('Charlie Evacuation Transport', 'EVAC-CHARLIE', 'EVACUATION_CREW', 'AVAILABLE', '+91-98765-01003', 'Northern Transit Depot', 8),
    ('Delta Urban Firefighting Unit', 'FIRE-DELTA', 'FIREFIGHTING', 'EN_ROUTE', '+91-98765-01004', 'Fire Station 4 Command', 5),
    ('Echo Hazardous Materials Squad', 'HAZ-ECHO', 'HAZMAT', 'AVAILABLE', '+91-98765-01005', 'Industrial Safety Depot', 4)
ON CONFLICT (name) DO UPDATE SET
    callsign = EXCLUDED.callsign,
    team_type = EXCLUDED.team_type,
    status = EXCLUDED.status,
    contact_phone = EXCLUDED.contact_phone,
    base_location = EXCLUDED.base_location,
    capacity = EXCLUDED.capacity,
    updated_at = NOW();

-- 2. Ensure admin@blackout.ops has SUPER_ADMIN role in public.user_profiles
UPDATE public.user_profiles
SET role = 'SUPER_ADMIN'
WHERE email = 'admin@blackout.ops';

-- 3. Upgrade primary administrator account to SUPER_ADMIN
UPDATE public.user_profiles
SET role = 'SUPER_ADMIN'
WHERE email = 'tharunsai2307@gmail.com';
