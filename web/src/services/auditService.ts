import { supabase } from './supabase';
import { AuditLog } from '../types/database';
import { offlineStore, isPlaceholderUrl } from './offlineStore';

export const auditService = {
  async getAuditLogs(options?: {
    resourceType?: string;
    action?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ data: AuditLog[]; count: number; error: string | null }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      let logs = offlineStore.getAuditLogs();
      if (options?.action) {
        logs = logs.filter((l) => l.action.includes(options.action!));
      }
      return { data: logs, count: logs.length, error: null };
    }

    try {
      let query = supabase
        .from('audit_logs')
        .select('*', { count: 'exact' })
        .order('timestamp', { ascending: false });

      if (options?.resourceType) {
        query = query.eq('resource_type', options.resourceType);
      }
      if (options?.action) {
        query = query.ilike('action', `%${options.action}%`);
      }

      const limit = options?.limit || 50;
      const offset = options?.offset || 0;
      query = query.range(offset, offset + limit - 1);

      const { data, count, error } = await query;
      if (error) throw error;

      return {
        data: (data as AuditLog[]) || [],
        count: count || 0,
        error: null,
      };
    } catch (err: any) {
      console.warn('auditService network error, falling back to offline audit ledger:', err.message);
      let logs = offlineStore.getAuditLogs();
      if (options?.action) {
        logs = logs.filter((l) => l.action.includes(options.action!));
      }
      return { data: logs, count: logs.length, error: null };
    }
  },

  async logAction(params: {
    actorId?: string | null;
    actorEmail: string;
    actorRole: string;
    action: string;
    resourceType: string;
    resourceId: string;
    details?: Record<string, any>;
  }): Promise<boolean> {
    const newLog: AuditLog = {
      id: `audit-${Date.now()}`,
      actor_id: params.actorId || null,
      actor_email: params.actorEmail,
      actor_role: params.actorRole,
      action: params.action,
      resource_type: params.resourceType,
      resource_id: params.resourceId,
      timestamp: new Date().toISOString(),
      details: params.details || {},
    };

    offlineStore.addAuditLog(newLog);

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) return true;

    try {
      await supabase.from('audit_logs').insert({
        actor_id: params.actorId || null,
        actor_email: params.actorEmail,
        actor_role: params.actorRole,
        action: params.action,
        resource_type: params.resourceType,
        resource_id: params.resourceId,
        details: params.details || {},
      });
      return true;
    } catch (err) {
      return true;
    }
  },
};
