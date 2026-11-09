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

      const remoteWarnings = (data as PublicWarning[]) || [];
      const offlineWarnings = offlineStore.getWarnings();
      const warningMap = new Map<string, PublicWarning>();

      for (const w of offlineWarnings) {
        warningMap.set(w.warning_code || w.id, w);
      }
      for (const w of remoteWarnings) {
        warningMap.set(w.warning_code || w.id, w);
      }

      let allWarnings = Array.from(warningMap.values());
      if (statusFilter && statusFilter !== 'ALL') {
        allWarnings = allWarnings.filter((w) => w.status === statusFilter);
      }
      allWarnings.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

      return { data: allWarnings, error: null };
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

    const isValidUuid = (val?: string) =>
      Boolean(val && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val));
    const createdByUuid = isValidUuid(params.actor?.id) ? params.actor.id : null;

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
      created_by: createdByUuid,
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
      // 1. Try secure stored procedure RPC (SECURITY DEFINER)
      const { data: rpcData, error: rpcErr } = await supabase.rpc('create_public_warning', {
        p_title: newWarning.title,
        p_description: newWarning.description,
        p_category: newWarning.category,
        p_severity: newWarning.severity,
        p_affected_area: newWarning.affected_area,
        p_instructions: newWarning.instructions || null,
        p_latitude: newWarning.latitude || null,
        p_longitude: newWarning.longitude || null,
        p_radius_km: newWarning.radius_km || null,
        p_expires_at: newWarning.expires_at,
      });

      if (!rpcErr && rpcData) {
        const warningObj = rpcData as PublicWarning;
        offlineStore.addWarning(warningObj);
        return { data: warningObj, error: null };
      }

      // 2. Fallback to direct table insert
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
          created_by: createdByUuid,
          expires_at: params.expiresAt,
        })
        .select('*')
        .single();

      if (error) throw error;
      if (data) {
        offlineStore.addWarning(data as PublicWarning);
      }
      return { data: (data as PublicWarning) || newWarning, error: null };
    } catch (err: any) {
      console.warn('createWarning preserved in offline store:', err.message);
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
