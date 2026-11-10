import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import { User } from '@supabase/supabase-js';
import { UserProfile, AppRole } from '../types/database';
import { authService } from '../services/authService';

interface AuthContextType {
  user: User | null;
  profile: UserProfile | null;
  role: AppRole | null;
  loading: boolean;
  isAuthenticated: boolean;
  login: (email: string, pass: string) => Promise<{ success: boolean; error: string | null }>;
  logout: () => Promise<void>;
  hasRole: (allowedRoles: AppRole[]) => boolean;
  canVerify: () => boolean;
  canDispatch: () => boolean;
  canPublishWarnings: () => boolean;
  isSuperAdmin: () => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let mounted = true;

    async function initAuth() {
      try {
        const session = await authService.getCurrentSession();
        if (session?.user && mounted) {
          setUser(session.user);
          const prof = await authService.getUserProfile(session.user.id, session.user.email);
          if (mounted) setProfile(prof);
        } else {
          const localUser = await authService.getCurrentUser();
          if (localUser && mounted) {
            setUser(localUser as any);
            const prof = await authService.getUserProfile(localUser.id, localUser.email);
            if (mounted) setProfile(prof);
          }
        }
      } catch (err) {
        console.error('Failed to initialize auth state:', err);
      } finally {
        if (mounted) setLoading(false);
      }
    }

    initAuth();

    const { data: authListener } = authService.onAuthStateChange(async (_event, session) => {
      if (!mounted) return;
      if (session?.user) {
        setUser(session.user);
        const prof = await authService.getUserProfile(session.user.id, session.user.email);
        if (mounted) setProfile(prof);
      } else {
        setUser(null);
        setProfile(null);
      }
      setLoading(false);
    });

    return () => {
      mounted = false;
      authListener?.subscription?.unsubscribe();
    };
  }, []);

  const login = async (email: string, pass: string) => {
    setLoading(true);
    const { profile: userProfile, error } = await authService.login(email, pass);
    if (error || !userProfile) {
      setLoading(false);
      return { success: false, error: error || 'Authentication failed' };
    }
    const currentUser = await authService.getCurrentUser();
    setUser(currentUser);
    setProfile(userProfile);
    setLoading(false);
    return { success: true, error: null };
  };

  const logout = async () => {
    setLoading(true);
    await authService.logout();
    setUser(null);
    setProfile(null);
    setLoading(false);
  };

  const role = profile?.role || null;

  const hasRole = (allowedRoles: AppRole[]): boolean => {
    if (!role) return false;
    if (role === 'SUPER_ADMIN') return true; // Super admin possesses superuser privileges
    return allowedRoles.includes(role);
  };

  const canVerify = (): boolean => hasRole(['SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR']);
  const canDispatch = (): boolean => hasRole(['SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR', 'FIELD_RESPONSE_COORDINATOR']);
  const canPublishWarnings = (): boolean => hasRole(['SUPER_ADMIN', 'PUBLIC_WARNING_PUBLISHER', 'CONTROL_ROOM_OPERATOR']);
  const isSuperAdmin = (): boolean => role === 'SUPER_ADMIN';

  const value = useMemo(
    () => ({
      user,
      profile,
      role,
      loading,
      isAuthenticated: !!user || !!profile,
      login,
      logout,
      hasRole,
      canVerify,
      canDispatch,
      canPublishWarnings,
      isSuperAdmin,
    }),
    [user, profile, role, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
