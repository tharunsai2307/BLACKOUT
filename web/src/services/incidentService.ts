import { supabase } from './supabase';
import { EmergencyAlert, VerificationStatus, OperatorStatus, EmergencyPriority } from '../types/database';
import { offlineStore, isPlaceholderUrl } from './offlineStore';

export interface IncidentFilterOptions {
  category?: string;
  priority?: EmergencyPriority | 'ALL';
  operatorStatus?: OperatorStatus | 'ALL';
  verificationStatus?: VerificationStatus | 'ALL';
  search?: string;
  dateFrom?: string;
  dateTo?: string;
  hasCoordinatesOnly?: boolean;
  limit?: number;
  offset?: number;
  sortBy?: 'created_at' | 'priority' | 'hop_count';
  sortOrder?: 'asc' | 'desc';
}

export const incidentService = {
  async getIncidents(options?: IncidentFilterOptions): Promise<{
    data: EmergencyAlert[];
    count: number;
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    // If Supabase URL is unconfigured, immediately serve offline operational records
    if (isPlaceholderUrl(supabaseUrl)) {
      return this.getOfflineIncidents(options);
    }

    try {
      let query = supabase
        .from('emergency_alerts')
        .select('*, assigned_team:response_teams(id, name, callsign, status)', { count: 'exact' });

      // Apply Filters
      if (options?.category && options.category !== 'ALL') {
        query = query.eq('category', options.category);
      }
      if (options?.priority && options.priority !== 'ALL') {
        query = query.eq('priority', options.priority);
      }
      if (options?.operatorStatus && options.operatorStatus !== 'ALL') {
        query = query.eq('operator_status', options.operatorStatus);
      }
      if (options?.verificationStatus && options.verificationStatus !== 'ALL') {
        query = query.eq('verification_status', options.verificationStatus);
      }
      if (options?.hasCoordinatesOnly) {
        query = query.not('latitude', 'is', null).not('longitude', 'is', null);
      }
      if (options?.dateFrom) {
        query = query.gte('created_at', options.dateFrom);
      }
      if (options?.dateTo) {
        query = query.lte('created_at', options.dateTo);
      }
      if (options?.search && options.search.trim()) {
        const term = options.search.trim();
        query = query.or(
          `description.ilike.%${term}%,message_id.ilike.%${term}%,gateway_device_id.ilike.%${term}%,original_sender_device_id.ilike.%${term}%`
        );
      }

      // Sorting
      const sortBy = options?.sortBy || 'created_at';
      const sortOrder = options?.sortOrder === 'asc';
      query = query.order(sortBy, { ascending: sortOrder });

      // Pagination
      const limit = options?.limit || 20;
      const offset = options?.offset || 0;
      query = query.range(offset, offset + limit - 1);

      let { data, count, error } = await query;
      if (error) {
        console.warn('Initial join query on emergency_alerts failed, retrying without assigned_team relation:', error.message);
        let fallbackQuery = supabase
          .from('emergency_alerts')
          .select('*', { count: 'exact' });

        if (options?.category && options.category !== 'ALL') {
          fallbackQuery = fallbackQuery.eq('category', options.category);
        }
        if (options?.priority && options.priority !== 'ALL') {
          fallbackQuery = fallbackQuery.eq('priority', options.priority);
        }
        if (options?.operatorStatus && options.operatorStatus !== 'ALL') {
          fallbackQuery = fallbackQuery.eq('operator_status', options.operatorStatus);
        }
        if (options?.verificationStatus && options.verificationStatus !== 'ALL') {
          fallbackQuery = fallbackQuery.eq('verification_status', options.verificationStatus);
        }
        if (options?.hasCoordinatesOnly) {
          fallbackQuery = fallbackQuery.not('latitude', 'is', null).not('longitude', 'is', null);
        }
        if (options?.dateFrom) {
          fallbackQuery = fallbackQuery.gte('created_at', options.dateFrom);
        }
        if (options?.dateTo) {
          fallbackQuery = fallbackQuery.lte('created_at', options.dateTo);
        }
        if (options?.search && options.search.trim()) {
          const term = options.search.trim();
          fallbackQuery = fallbackQuery.or(
            `description.ilike.%${term}%,message_id.ilike.%${term}%,gateway_device_id.ilike.%${term}%,original_sender_device_id.ilike.%${term}%`
          );
        }
        fallbackQuery = fallbackQuery.order(sortBy, { ascending: sortOrder }).range(offset, offset + limit - 1);
        const retryRes = await fallbackQuery;
        if (retryRes.error) throw retryRes.error;
        data = retryRes.data;
        count = retryRes.count;
      }

      return {
        data: (data as EmergencyAlert[]) || [],
        count: count || 0,
        error: null,
      };
    } catch (err: any) {
      console.warn('incidentService.getIncidents failed over network, falling back to offline store:', err.message);
      return this.getOfflineIncidents(options);
    }
  },

  getOfflineIncidents(options?: IncidentFilterOptions): {
    data: EmergencyAlert[];
    count: number;
    error: string | null;
  } {
    let alerts = offlineStore.getAlerts();

    if (options?.category && options.category !== 'ALL') {
      alerts = alerts.filter((a) => a.category === options.category);
    }
    if (options?.priority && options.priority !== 'ALL') {
      alerts = alerts.filter((a) => a.priority === options.priority);
    }
    if (options?.verificationStatus && options.verificationStatus !== 'ALL') {
      alerts = alerts.filter((a) => a.verification_status === options.verificationStatus);
    }
    if (options?.search && options.search.trim()) {
      const term = options.search.trim().toLowerCase();
      alerts = alerts.filter(
        (a) =>
          a.description?.toLowerCase().includes(term) ||
          a.message_id.toLowerCase().includes(term) ||
          a.gateway_device_id.toLowerCase().includes(term) ||
          a.original_sender_device_id.toLowerCase().includes(term)
      );
    }

    const total = alerts.length;
    const offset = options?.offset || 0;
    const limit = options?.limit || 20;
    const paginated = alerts.slice(offset, offset + limit);

    return { data: paginated, count: total, error: null };
  },

  async getIncidentById(id: string): Promise<{
    alert: EmergencyAlert | null;
    uploadHistory: any[];
    events: any[];
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      const alert = offlineStore.getAlertById(id);
      return {
        alert,
        uploadHistory: [
          {
            id: 'upl-1',
            message_id: id,
            gateway_device_id: alert?.gateway_device_id || 'GW-NODE-1',
            upload_completed_at: alert?.created_at || new Date().toISOString(),
            upload_status: 'SUCCESS',
            response_reference: 'REF-OFFLINE-SYNC',
          },
        ],
        events: [
          {
            id: 'ev-1',
            event_type: 'INGESTED_VIA_GATEWAY',
            event_timestamp: alert?.created_at || new Date().toISOString(),
            actor_id: 'GATEWAY_DAEMON',
          },
        ],
        error: alert ? null : 'Incident not found',
      };
    }

    try {
      let alert: any = null;
      const primaryRes = await supabase
        .from('emergency_alerts')
        .select('*, assigned_team:response_teams(id, name, callsign, status, team_type)')
        .or(`id.eq.${id},message_id.eq.${id}`)
        .single();

      if (primaryRes.error) {
        // Fallback without relation join
        const retryRes = await supabase
          .from('emergency_alerts')
          .select('*')
          .or(`id.eq.${id},message_id.eq.${id}`)
          .single();
        if (retryRes.error) throw retryRes.error;
        alert = retryRes.data;
      } else {
        alert = primaryRes.data;
      }

      if (!alert) return { alert: null, uploadHistory: [], events: [], error: 'Incident not found' };

      // Fetch Gateway upload history
      const { data: uploadHistory } = await supabase
        .from('gateway_upload_history')
        .select('*')
        .eq('message_id', alert.message_id)
        .order('upload_completed_at', { ascending: false });

      // Fetch audit events
      const { data: events } = await supabase
        .from('emergency_alert_events')
        .select('*')
        .eq('message_id', alert.message_id)
        .order('event_timestamp', { ascending: false });

      return {
        alert: alert as EmergencyAlert,
        uploadHistory: uploadHistory || [],
        events: events || [],
        error: null,
      };
    } catch (err: any) {
      console.warn('incidentService.getIncidentById network error, checking offline store:', err.message);
      const alert = offlineStore.getAlertById(id);
      return { alert, uploadHistory: [], events: [], error: alert ? null : err.message };
    }
  },

  async updateVerification(params: {
    alertId: string;
    messageId: string;
    newVerificationStatus: VerificationStatus;
    rejectionReason?: string;
    operatorNotes?: string;
    actor: { id?: string; email: string; role: string };
  }): Promise<{ success: boolean; error: string | null }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    // Offline Store Update
    offlineStore.updateAlert(params.alertId, {
      verification_status: params.newVerificationStatus,
      operator_notes: params.operatorNotes,
      rejection_reason: params.rejectionReason,
      verified_at: new Date().toISOString(),
      verified_by: params.actor.id,
    });

    if (isPlaceholderUrl(supabaseUrl)) {
      return { success: true, error: null };
    }

    try {
      const now = new Date().toISOString();
      const updates: any = {
        verification_status: params.newVerificationStatus,
        verified_at: now,
        verified_by: params.actor.id || null,
        updated_at: now,
      };

      if (params.rejectionReason) {
        updates.rejection_reason = params.rejectionReason;
      }
      if (params.operatorNotes) {
        updates.operator_notes = params.operatorNotes;
      }

      if (params.newVerificationStatus === 'VERIFIED') {
        updates.operator_status = 'TRIAGED';
      } else if (params.newVerificationStatus === 'UNDER_REVIEW') {
        updates.operator_status = 'ACKNOWLEDGED';
      } else if (params.newVerificationStatus === 'FALSE_REPORT') {
        updates.operator_status = 'CLOSED';
      }

      const { error } = await supabase
        .from('emergency_alerts')
        .update(updates)
        .eq('id', params.alertId);

      if (error) throw error;

      return { success: true, error: null };
    } catch (err: any) {
      console.warn('Supabase updateVerification error:', err.message);
      return { success: true, error: null }; // Updated locally
    }
  },

  async assignTeam(params: {
    alertId: string;
    messageId: string;
    teamId: string;
    teamName: string;
    actor: { id?: string; email: string; role: string };
    notes?: string;
  }): Promise<{ success: boolean; error: string | null }> {
    const targetTeam = offlineStore.getTeamById(params.teamId);
    offlineStore.updateAlert(params.alertId, {
      assigned_team_id: params.teamId,
      assigned_team: targetTeam,
      operator_status: 'DISPATCHED',
    });
    offlineStore.updateTeamStatus(params.teamId, 'ASSIGNED');

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) {
      return { success: true, error: null };
    }

    try {
      const now = new Date().toISOString();
      await supabase
        .from('emergency_alerts')
        .update({
          assigned_team_id: params.teamId,
          operator_status: 'DISPATCHED',
          updated_at: now,
        })
        .eq('id', params.alertId);

      await supabase
        .from('response_teams')
        .update({
          status: 'ASSIGNED',
          active_incident_id: params.alertId,
          updated_at: now,
        })
        .eq('id', params.teamId);

      return { success: true, error: null };
    } catch (err: any) {
      console.warn('Supabase assignTeam error:', err.message);
      return { success: true, error: null };
    }
  },

  async getIncidentStats(): Promise<{
    totalReceived: number;
    awaitingVerification: number;
    verifiedActive: number;
    assignedCount: number;
    resolvedCount: number;
    expiredCount: number;
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      const all = offlineStore.getAlerts();
      return {
        totalReceived: all.length,
        awaitingVerification: all.filter((a) => a.verification_status === 'UNVERIFIED').length,
        verifiedActive: all.filter((a) => a.verification_status === 'VERIFIED').length,
        assignedCount: all.filter((a) => !!a.assigned_team_id).length,
        resolvedCount: 0,
        expiredCount: 0,
        error: null,
      };
    }

    try {
      const { data: alerts, error } = await supabase
        .from('emergency_alerts')
        .select('verification_status, operator_status, assigned_team_id, expires_at');

      if (error) throw error;

      const totalReceived = alerts.length;
      const awaitingVerification = alerts.filter((a) => a.verification_status === 'UNVERIFIED').length;
      const verifiedActive = alerts.filter((a) => a.verification_status === 'VERIFIED').length;
      const assignedCount = alerts.filter((a) => !!a.assigned_team_id).length;
      const resolvedCount = alerts.filter((a) => a.operator_status === 'RESOLVED' || a.operator_status === 'CLOSED').length;
      const expiredCount = alerts.filter((a) => new Date(a.expires_at).getTime() < Date.now()).length;

      return {
        totalReceived,
        awaitingVerification,
        verifiedActive,
        assignedCount,
        resolvedCount,
        expiredCount,
        error: null,
      };
    } catch (err: any) {
      console.warn('getIncidentStats failed over network, serving offline store counts:', err.message);
      const all = offlineStore.getAlerts();
      return {
        totalReceived: all.length,
        awaitingVerification: all.filter((a) => a.verification_status === 'UNVERIFIED').length,
        verifiedActive: all.filter((a) => a.verification_status === 'VERIFIED').length,
        assignedCount: all.filter((a) => !!a.assigned_team_id).length,
        resolvedCount: 0,
        expiredCount: 0,
        error: null,
      };
    }
  },
};
