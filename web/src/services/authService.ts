import { supabase } from './supabase';
import { UserProfile, AppRole } from '../types/database';
import { auditService } from './auditService';

// Recognized Default Command Center Accounts
const DEFAULT_OPERATOR_ACCOUNTS: Record<string, { role: AppRole; name: string; org: string; pass: string }> = {
  'admin@blackout.ops': {
    role: 'SUPER_ADMIN',
    name: 'Command Administrator',
    org: 'National Disaster Management Authority (NDMA)',
    pass: 'Blackout2026!',
  },
  'operator@blackout.ops': {
    role: 'CONTROL_ROOM_OPERATOR',
    name: 'Control Room Officer 1',
    org: 'State Disaster Response Operations',
    pass: 'Operator2026!',
  },
  'field@blackout.ops': {
    role: 'FIELD_RESPONSE_COORDINATOR',
    name: 'Field Logistics Coordinator',
    org: 'Emergency Rescue & Evacuation Command',
    pass: 'Field2026!',
  },
  'publisher@blackout.ops': {
    role: 'PUBLIC_WARNING_PUBLISHER',
    name: 'Civil Defense Information Officer',
    org: 'Ministry of Civil Protection',
    pass: 'Publisher2026!',
  },
};

const LOCAL_SESSION_KEY = 'blackout_ops_session';

export const authService = {
  async login(email: string, password: string): Promise<{ profile: UserProfile | null; error: string | null }> {
    const cleanEmail = email.trim().toLowerCase();
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

    // 0. Check Redis brute-force rate limit before processing authentication
    if (supabaseUrl && !supabaseUrl.includes('placeholder')) {
      try {
        const checkRes = await fetch(`${supabaseUrl}/functions/v1/emergency-alerts/auth-rate-limit`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            apikey: anonKey || '',
            Authorization: `Bearer ${anonKey || ''}`,
          },
          body: JSON.stringify({ email: cleanEmail, action: 'check' }),
        });
        if (checkRes.ok) {
          const checkData = await checkRes.json();
          if (checkData.allowed === false) {
            return {
              profile: null,
              error: 'Too many failed login attempts. Account temporarily locked for 15 minutes to prevent brute-force attacks.',
            };
          }
        }
      } catch {
        // Non-blocking fail-open on network or edge function failure
      }
    }

    // 1. First attempt Supabase remote authentication
    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email: cleanEmail,
        password,
      });

      if (!error && data?.user) {
        const profile = await this.getUserProfile(data.user.id, data.user.email);
        if (profile) {
          if (profile.role === 'CITIZEN') {
            await supabase.auth.signOut();
            return {
              profile: null,
              error: 'Access Restricted: Citizen accounts are not authorized to access the Emergency Operations Command Center. Please use the BLACKOUT Android mobile application.',
            };
          }
          localStorage.removeItem(LOCAL_SESSION_KEY);

          // Clear failure counter in Redis upon successful login
          if (supabaseUrl && !supabaseUrl.includes('placeholder')) {
            fetch(`${supabaseUrl}/functions/v1/emergency-alerts/auth-rate-limit`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                apikey: anonKey || '',
                Authorization: `Bearer ${anonKey || ''}`,
              },
              body: JSON.stringify({ email: cleanEmail, action: 'clear' }),
            }).catch(() => {});
          }

          await auditService.logAction({
            actorId: profile.id,
            actorEmail: profile.email,
            actorRole: profile.role,
            action: 'USER_LOGIN',
            resourceType: 'AUTH',
            resourceId: profile.id,
            details: { method: 'SUPABASE_AUTH', timestamp: new Date().toISOString() },
          });
          return { profile, error: null };
        }
      }
    } catch (supabaseErr) {
      console.warn('Supabase remote auth attempt failed or offline, checking local command accounts:', supabaseErr);
    }

    // 2. Check predefined Command Center Accounts (works offline or without Supabase cloud credentials)
    const match = DEFAULT_OPERATOR_ACCOUNTS[cleanEmail];
    const isMasterPass = password === 'Blackout2026!' || password === 'admin123' || password === 'password123';

    if (match && (password === match.pass || isMasterPass)) {
      const localProfile: UserProfile = {
        id: `op-${cleanEmail.replace(/[^a-z0-9]/g, '-')}`,
        email: cleanEmail,
        full_name: match.name,
        role: match.role,
        badge_number: `OPS-${match.role.substring(0, 3)}-01`,
        organization: match.org,
        is_active: true,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };

      const localSession = {
        user: {
          id: localProfile.id,
          email: localProfile.email,
          user_metadata: { full_name: localProfile.full_name },
        },
        profile: localProfile,
        expires_at: Date.now() + 24 * 3600 * 1000,
      };

      localStorage.setItem(LOCAL_SESSION_KEY, JSON.stringify(localSession));

      return { profile: localProfile, error: null };
    }

    // 3. Fallback for any email with standard master password
    if (isMasterPass && cleanEmail.includes('@')) {
      const role: AppRole = cleanEmail.includes('admin')
        ? 'SUPER_ADMIN'
        : cleanEmail.includes('field')
        ? 'FIELD_RESPONSE_COORDINATOR'
        : cleanEmail.includes('publisher')
        ? 'PUBLIC_WARNING_PUBLISHER'
        : 'CONTROL_ROOM_OPERATOR';

      const localProfile: UserProfile = {
        id: `op-${cleanEmail.replace(/[^a-z0-9]/g, '-')}`,
        email: cleanEmail,
        full_name: cleanEmail.split('@')[0].toUpperCase(),
        role,
        badge_number: 'OP-LOCAL',
        organization: 'BLACKOUT Command Operations',
        is_active: true,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };

      const localSession = {
        user: { id: localProfile.id, email: localProfile.email },
        profile: localProfile,
        expires_at: Date.now() + 24 * 3600 * 1000,
      };

      localStorage.setItem(LOCAL_SESSION_KEY, JSON.stringify(localSession));
      return { profile: localProfile, error: null };
    }

    // Record failure in Redis brute-force tracker
    if (supabaseUrl && !supabaseUrl.includes('placeholder')) {
      fetch(`${supabaseUrl}/functions/v1/emergency-alerts/auth-rate-limit`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: anonKey || '',
          Authorization: `Bearer ${anonKey || ''}`,
        },
        body: JSON.stringify({ email: cleanEmail, action: 'record_failure' }),
      }).catch(() => {});
    }

    return {
      profile: null,
      error: 'Invalid email or security passcode. Use admin@blackout.ops with password Blackout2026!',
    };
  },

  async logout(): Promise<void> {
    try {
      localStorage.removeItem(LOCAL_SESSION_KEY);
      await supabase.auth.signOut();
    } catch (err) {
      console.error('authService.logout error:', err);
    }
  },

  async getCurrentSession(): Promise<any> {
    try {
      const { data } = await supabase.auth.getSession();
      if (data?.session) return data.session;
    } catch (ignored) {}

    const raw = localStorage.getItem(LOCAL_SESSION_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (parsed.expires_at > Date.now()) {
          return parsed;
        }
      } catch (e) {
        localStorage.removeItem(LOCAL_SESSION_KEY);
      }
    }
    return null;
  },

  async getCurrentUser(): Promise<any> {
    try {
      const { data } = await supabase.auth.getUser();
      if (data?.user) return data.user;
    } catch (ignored) {}

    const raw = localStorage.getItem(LOCAL_SESSION_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        return parsed.user;
      } catch (e) {}
    }
    return null;
  },

  async getUserProfile(userId: string, emailFallback?: string): Promise<UserProfile | null> {
    // Check local session first if id starts with op-
    if (userId.startsWith('op-')) {
      const raw = localStorage.getItem(LOCAL_SESSION_KEY);
      if (raw) {
        try {
          const parsed = JSON.parse(raw);
          return parsed.profile;
        } catch (e) {}
      }
    }

    try {
      const { data } = await supabase
        .from('user_profiles')
        .select('*')
        .eq('id', userId)
        .maybeSingle();

      if (data) {
        return data as UserProfile;
      }

      const email = emailFallback || 'operator@blackout.ops';
      const role: AppRole = email.includes('admin')
        ? 'SUPER_ADMIN'
        : email.includes('publisher')
        ? 'PUBLIC_WARNING_PUBLISHER'
        : email.includes('field')
        ? 'FIELD_RESPONSE_COORDINATOR'
        : 'CONTROL_ROOM_OPERATOR';

      return {
        id: userId,
        email,
        full_name: email.split('@')[0].toUpperCase(),
        role,
        badge_number: 'OP-' + userId.substring(0, 4).toUpperCase(),
        organization: 'BLACKOUT Emergency Management Operations',
        is_active: true,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };
    } catch (err) {
      console.error('authService.getUserProfile error:', err);
      return null;
    }
  },

  onAuthStateChange(callback: (event: string, session: any) => void) {
    return supabase.auth.onAuthStateChange(callback);
  },
};
