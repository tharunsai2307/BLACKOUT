import { supabase } from './supabase';
import { PublicWarning, WarningStatus, WarningSeverity } from '../types/database';
import { offlineStore, isPlaceholderUrl } from './offlineStore';

export const warningService = {
  async getWarnings(statusFilter?: WarningStatus | 'ALL'): Promise<{
    data: PublicWarning[];
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      return { data: offlineStore.getWarnings(statusFilter), error: null };
    }

    try {
      let query = supabase
        .from('public_warnings')
        .select('*')
        .order('created_at', { ascending: false });

      if (statusFilter && statusFilter !== 'ALL') {
        query = query.eq('status', statusFilter);
      }

      const { data, error } = await query;
      if (error) throw error;

      return { data: (data as PublicWarning[]) || [], error: null };
    } catch (err: any) {
      console.warn('warningService.getWarnings network error, falling back to offline warnings:', err.message);
      return { data: offlineStore.getWarnings(statusFilter), error: null };
    }
  },

  async getWarningById(id: string): Promise<{ warning: PublicWarning | null; error: string | null }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      const w = offlineStore.getWarnings().find((item) => item.id === id) || null;
      return { warning: w, error: w ? null : 'Warning not found' };
    }

    try {
      const { data, error } = await supabase
        .from('public_warnings')
        .select('*')
        .eq('id', id)
        .single();

      if (error) throw error;
      return { warning: data as PublicWarning, error: null };
    } catch (err: any) {
      const w = offlineStore.getWarnings().find((item) => item.id === id) || null;
      return { warning: w, error: w ? null : err.message };
    }
  },

  async createWarning(params: {
    title: string;
    description: string;
    category: string;
    severity: WarningSeverity;
    affectedArea: string;
    latitude?: number | null;
    longitude?: number | null;
    radiusKm?: number | null;
    instructions?: string;
    expiresAt: string;
    actor: { id?: string; email: string; role: string };
  }): Promise<{ data: PublicWarning | null; error: string | null }> {
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    const year = new Date().getFullYear();
    const warningCode = `WARN-${year}-${randomSuffix}`;

    const newWarning: PublicWarning = {
      id: `warn-${Date.now()}`,
      warning_code: warningCode,
      title: params.title.trim(),
      description: params.description.trim(),
      category: params.category,
      severity: params.severity,
      affected_area: params.affectedArea.trim(),
      latitude: params.latitude || null,
      longitude: params.longitude || null,
      radius_km: params.radiusKm || null,
      instructions: params.instructions || null,
      issuing_authority: 'National Emergency Operations Command',
      status: 'DRAFT',
      created_by: params.actor.id || null,
      expires_at: params.expiresAt,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    offlineStore.addWarning(newWarning);

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) {
      return { data: newWarning, error: null };
    }

    try {
      const { data, error } = await supabase
        .from('public_warnings')
        .insert({
          warning_code: warningCode,
          title: newWarning.title,
          description: newWarning.description,
          category: newWarning.category,
          severity: newWarning.severity,
          affected_area: newWarning.affected_area,
          instructions: newWarning.instructions,
          status: 'DRAFT',
          created_by: params.actor.id || null,
          expires_at: params.expiresAt,
        })
        .select('*')
        .single();

      if (error) throw error;
      return { data: data as PublicWarning, error: null };
    } catch (err: any) {
      console.warn('createWarning remote insert failed, saved to offline store:', err.message);
      return { data: newWarning, error: null };
    }
  },

  async submitForApproval(
    warningId: string,
    _actor?: { id?: string; email: string; role: string }
  ): Promise<{ success: boolean; error: string | null }> {
    offlineStore.updateWarningStatus(warningId, 'SUBMITTED_FOR_APPROVAL');

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) return { success: true, error: null };

    try {
      const { error } = await supabase
        .from('public_warnings')
        .update({ status: 'SUBMITTED_FOR_APPROVAL', updated_at: new Date().toISOString() })
        .eq('id', warningId);
      if (error) throw error;
      return { success: true, error: null };
    } catch (err: any) {
      return { success: true, error: null };
    }
  },

  async approveWarning(
    warningId: string,
    _actor?: { id?: string; email: string; role: string }
  ): Promise<{ success: boolean; error: string | null }> {
    offlineStore.updateWarningStatus(warningId, 'APPROVED');

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) return { success: true, error: null };

    try {
      const { error } = await supabase
        .from('public_warnings')
        .update({ status: 'APPROVED', updated_at: new Date().toISOString() })
        .eq('id', warningId);
      if (error) throw error;
      return { success: true, error: null };
    } catch (err: any) {
      return { success: true, error: null };
    }
  },

  async publishWarning(
    warningId: string,
    _actor?: { id?: string; email: string; role: string }
  ): Promise<{ success: boolean; error: string | null }> {
    offlineStore.updateWarningStatus(warningId, 'PUBLISHED');

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) return { success: true, error: null };

    try {
      const { error } = await supabase
        .from('public_warnings')
        .update({
          status: 'PUBLISHED',
          published_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        })
        .eq('id', warningId);
      if (error) throw error;
      return { success: true, error: null };
    } catch (err: any) {
      return { success: true, error: null };
    }
  },

  async revokeWarning(
    warningId: string,
    reason: string,
    _actor?: { id?: string; email: string; role: string }
  ): Promise<{ success: boolean; error: string | null }> {
    offlineStore.updateWarningStatus(warningId, 'REVOKED');

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) return { success: true, error: null };

    try {
      const { error } = await supabase
        .from('public_warnings')
        .update({
          status: 'REVOKED',
          revoked_at: new Date().toISOString(),
          revocation_reason: reason.trim(),
          updated_at: new Date().toISOString(),
        })
        .eq('id', warningId);
      if (error) throw error;
      return { success: true, error: null };
    } catch (err: any) {
      return { success: true, error: null };
    }
  },
};
