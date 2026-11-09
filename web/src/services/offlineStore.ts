import { EmergencyAlert, ResponseTeam, PublicWarning, AuditLog } from '../types/database';

export const isPlaceholderUrl = (url?: string): boolean => {
  return !url || url.includes('your-project-id') || url.includes('placeholder');
};

const SEED_TEAMS: ResponseTeam[] = [
  {
    id: 'team-alpha',
    name: 'Alpha Rapid Search & Rescue',
    callsign: 'SAR-ALPHA',
    team_type: 'SEARCH_AND_RESCUE',
    status: 'AVAILABLE',
    contact_phone: '+1-800-555-0101',
    base_location: 'Sector 1 Command Post',
    capacity: 6,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    updated_at: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'team-bravo',
    name: 'Bravo Emergency Medical Corps',
    callsign: 'MED-BRAVO',
    team_type: 'MEDICAL_RESPONSE',
    status: 'AVAILABLE',
    contact_phone: '+1-800-555-0102',
    base_location: 'Central Field Hospital',
    capacity: 4,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    updated_at: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'team-charlie',
    name: 'Charlie Evacuation Transport',
    callsign: 'EVAC-CHARLIE',
    team_type: 'EVACUATION_CREW',
    status: 'AVAILABLE',
    contact_phone: '+1-800-555-0103',
    base_location: 'North Transport Staging',
    capacity: 8,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    updated_at: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'team-delta',
    name: 'Delta Urban Firefighting Unit',
    callsign: 'FIRE-DELTA',
    team_type: 'FIREFIGHTING',
    status: 'EN_ROUTE',
    contact_phone: '+1-800-555-0104',
    base_location: 'Station 4 Tactical Base',
    capacity: 5,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    updated_at: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'team-echo',
    name: 'Echo Hazardous Materials Squad',
    callsign: 'HAZ-ECHO',
    team_type: 'HAZMAT',
    status: 'AVAILABLE',
    contact_phone: '+1-800-555-0105',
    base_location: 'South Logistics Depot',
    capacity: 4,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    updated_at: new Date(Date.now() - 3600000).toISOString(),
  },
];

const SEED_ALERTS: EmergencyAlert[] = [];

const SEED_WARNINGS: PublicWarning[] = [];

const SEED_AUDIT: AuditLog[] = [];

class OfflineStore {
  private alerts: EmergencyAlert[] = [...SEED_ALERTS];
  private teams: ResponseTeam[] = [...SEED_TEAMS];
  private warnings: PublicWarning[] = [...SEED_WARNINGS];
  private auditLogs: AuditLog[] = [...SEED_AUDIT];

  getAlerts(): EmergencyAlert[] {
    return [...this.alerts];
  }

  getAlertById(id: string): EmergencyAlert | null {
    return this.alerts.find((a) => a.id === id || a.message_id === id) || null;
  }

  updateAlert(id: string, updates: Partial<EmergencyAlert>): EmergencyAlert | null {
    const idx = this.alerts.findIndex((a) => a.id === id || a.message_id === id);
    if (idx === -1) return null;
    this.alerts[idx] = { ...this.alerts[idx], ...updates, updated_at: new Date().toISOString() };
    return this.alerts[idx];
  }

  getTeams(): ResponseTeam[] {
    return [...this.teams];
  }

  getTeamById(id: string): ResponseTeam | null {
    return this.teams.find((t) => t.id === id) || null;
  }

  addTeam(team: ResponseTeam): ResponseTeam {
    this.teams.push(team);
    return team;
  }

  updateTeamStatus(teamId: string, status: any): boolean {
    const team = this.teams.find((t) => t.id === teamId);
    if (!team) return false;
    team.status = status;
    team.updated_at = new Date().toISOString();
    return true;
  }

  getWarnings(statusFilter?: string): PublicWarning[] {
    if (!statusFilter || statusFilter === 'ALL') return [...this.warnings];
    return this.warnings.filter((w) => w.status === statusFilter);
  }

  addWarning(warning: PublicWarning): PublicWarning {
    this.warnings.unshift(warning);
    return warning;
  }

  updateWarningStatus(id: string, status: any): boolean {
    const warning = this.warnings.find((w) => w.id === id);
    if (!warning) return false;
    warning.status = status;
    warning.updated_at = new Date().toISOString();
    return true;
  }

  getAuditLogs(): AuditLog[] {
    return [...this.auditLogs];
  }

  addAuditLog(log: AuditLog): void {
    this.auditLogs.unshift(log);
  }
}

export const offlineStore = new OfflineStore();
