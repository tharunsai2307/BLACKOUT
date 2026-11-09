import { supabase } from './supabase';
import { ResponseTeam, TeamStatus, TeamType } from '../types/database';
import { offlineStore, isPlaceholderUrl } from './offlineStore';

export const teamService = {
  async getResponseTeams(statusFilter?: TeamStatus | 'ALL'): Promise<{
    data: ResponseTeam[];
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      let teams = offlineStore.getTeams();
      if (statusFilter && statusFilter !== 'ALL') {
        teams = teams.filter((t) => t.status === statusFilter);
      }
      return { data: teams, error: null };
    }

    try {
      let query = supabase
        .from('response_teams')
        .select('*, members:response_team_members(*)')
        .order('name', { ascending: true });

      if (statusFilter && statusFilter !== 'ALL') {
        query = query.eq('status', statusFilter);
      }

      const { data, error } = await query;
      if (error) throw error;

      const remoteTeams = (data as ResponseTeam[]) || [];
      const offlineTeams = offlineStore.getTeams();
      const teamMap = new Map<string, ResponseTeam>();

      // Preserve any locally registered teams that might not yet be in remote DB
      for (const t of offlineTeams) {
        teamMap.set(t.callsign.toUpperCase(), t);
      }
      for (const t of remoteTeams) {
        teamMap.set(t.callsign.toUpperCase(), t);
      }

      let allTeams = Array.from(teamMap.values());
      if (statusFilter && statusFilter !== 'ALL') {
        allTeams = allTeams.filter((t) => t.status === statusFilter);
      }

      return { data: allTeams, error: null };
    } catch (err: any) {
      console.warn('teamService.getResponseTeams network error, falling back to offline units:', err.message);
      let teams = offlineStore.getTeams();
      if (statusFilter && statusFilter !== 'ALL') {
        teams = teams.filter((t) => t.status === statusFilter);
      }
      return { data: teams, error: null };
    }
  },

  async getResponseTeamById(id: string): Promise<{ team: ResponseTeam | null; error: string | null }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      const team = offlineStore.getTeamById(id);
      return { team, error: team ? null : 'Unit not found' };
    }

    try {
      const { data, error } = await supabase
        .from('response_teams')
        .select('*, members:response_team_members(*)')
        .eq('id', id)
        .single();

      if (error) throw error;
      return { team: data as ResponseTeam, error: null };
    } catch (err: any) {
      console.warn('teamService.getResponseTeamById network error, checking offline store:', err.message);
      const team = offlineStore.getTeamById(id);
      return { team, error: team ? null : err.message };
    }
  },

  async createResponseTeam(params: {
    name: string;
    callsign: string;
    team_type: TeamType;
    contact_phone?: string;
    base_location?: string;
    capacity: number;
    actor: { id?: string; email: string; role: string };
  }): Promise<{ data: ResponseTeam | null; error: string | null }> {
    const newTeam: ResponseTeam = {
      id: `team-${Date.now()}`,
      name: params.name.trim(),
      callsign: params.callsign.trim().toUpperCase(),
      team_type: params.team_type,
      status: 'AVAILABLE',
      contact_phone: params.contact_phone || null,
      base_location: params.base_location || null,
      capacity: params.capacity || 4,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    offlineStore.addTeam(newTeam);

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) {
      return { data: newTeam, error: null };
    }

    try {
      // 1. Try secure stored procedure RPC (SECURITY DEFINER)
      const { data: rpcData, error: rpcErr } = await supabase.rpc('create_response_team', {
        p_name: newTeam.name,
        p_callsign: newTeam.callsign,
        p_team_type: newTeam.team_type,
        p_contact_phone: newTeam.contact_phone || null,
        p_base_location: newTeam.base_location || null,
        p_capacity: newTeam.capacity || 4,
      });

      if (!rpcErr && rpcData) {
        const teamObj = rpcData as ResponseTeam;
        offlineStore.addTeam(teamObj);
        return { data: teamObj, error: null };
      }

      // 2. Fallback to direct table insert
      const { data, error } = await supabase
        .from('response_teams')
        .insert({
          name: newTeam.name,
          callsign: newTeam.callsign,
          team_type: newTeam.team_type,
          status: 'AVAILABLE',
          contact_phone: newTeam.contact_phone,
          base_location: newTeam.base_location,
          capacity: newTeam.capacity,
        })
        .select('*')
        .single();

      if (error) throw error;

      if (data) {
        offlineStore.addTeam(data as ResponseTeam);
      }
      return { data: (data as ResponseTeam) || newTeam, error: null };
    } catch (err: any) {
      console.warn('teamService.createResponseTeam preserved in offline store:', err.message);
      return { data: newTeam, error: null };
    }
  },

  async updateTeamStatus(
    teamId: string,
    newStatus: TeamStatus,
    _actor?: { id?: string; email: string; role: string }
  ): Promise<{ success: boolean; error: string | null }> {
    offlineStore.updateTeamStatus(teamId, newStatus);

    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
    if (isPlaceholderUrl(supabaseUrl)) {
      return { success: true, error: null };
    }

    try {
      const { error } = await supabase
        .from('response_teams')
        .update({
          status: newStatus,
          updated_at: new Date().toISOString(),
        })
        .eq('id', teamId);

      if (error) throw error;
      return { success: true, error: null };
    } catch (err: any) {
      console.warn('teamService.updateTeamStatus remote error:', err.message);
      return { success: true, error: null };
    }
  },
};
