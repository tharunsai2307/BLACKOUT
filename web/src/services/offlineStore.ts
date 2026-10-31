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

const SEED_ALERTS: EmergencyAlert[] = [
  {
    id: '550e8400-e29b-41d4-a716-446655440001',
    message_id: '550e8400-e29b-41d4-a716-446655440001',
    original_sender_device_id: 'NODE-CIVIL-042',
    gateway_device_id: 'NODE-GW-RELAY-1',
    category: 'Medical Emergency',
    description: 'Elderly diabetic patient in insulin shock. Road blocked by fallen trees. Urgent medical kit needed.',
    latitude: 28.6139,
    longitude: 77.2090,
    accuracy_meters: 8.5,
    location_timestamp: new Date(Date.now() - 900000).toISOString(),
    created_at: new Date(Date.now() - 900000).toISOString(),
    expires_at: new Date(Date.now() + 172800000).toISOString(),
    received_at: new Date(Date.now() - 600000).toISOString(),
    hop_count: 2,
    priority: 'CRITICAL',
    ingestion_status: 'ACCEPTED',
    operator_status: 'NEW',
    protocol_version: 1,
    payload_hash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    verification_status: 'UNVERIFIED',
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440002',
    message_id: '550e8400-e29b-41d4-a716-446655440002',
    original_sender_device_id: 'NODE-CIVIL-089',
    gateway_device_id: 'NODE-GW-RELAY-2',
    category: 'Flood',
    description: 'Flash flood water level reached 1.5 meters on Main Street. 4 civilians trapped on roof.',
    latitude: 28.6250,
    longitude: 77.2180,
    accuracy_meters: 12.0,
    location_timestamp: new Date(Date.now() - 3600000).toISOString(),
    created_at: new Date(Date.now() - 3600000).toISOString(),
    expires_at: new Date(Date.now() + 172800000).toISOString(),
    received_at: new Date(Date.now() - 3000000).toISOString(),
    hop_count: 3,
    priority: 'CRITICAL',
    ingestion_status: 'ACCEPTED',
    operator_status: 'TRIAGED',
    protocol_version: 1,
    payload_hash: 'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0',
    verification_status: 'VERIFIED',
    assigned_team_id: 'team-charlie',
    assigned_team: SEED_TEAMS[2],
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440003',
    message_id: '550e8400-e29b-41d4-a716-446655440003',
    original_sender_device_id: 'NODE-CIVIL-112',
    gateway_device_id: 'NODE-GW-RELAY-1',
    category: 'Trapped / Stranded',
    description: 'Structural collapse of warehouse annex. Two workers trapped under light corrugated roof.',
    latitude: 28.6010,
    longitude: 77.1990,
    accuracy_meters: 15.0,
    location_timestamp: new Date(Date.now() - 7200000).toISOString(),
    created_at: new Date(Date.now() - 7200000).toISOString(),
    expires_at: new Date(Date.now() + 172800000).toISOString(),
    received_at: new Date(Date.now() - 6500000).toISOString(),
    hop_count: 1,
    priority: 'HIGH',
    ingestion_status: 'ACCEPTED',
    operator_status: 'TRIAGED',
    protocol_version: 1,
    payload_hash: '9f8e7d6c5b4a3210987654321fedcba0987654321fedcba0987654321fedcba0',
    verification_status: 'VERIFIED',
    assigned_team_id: 'team-alpha',
    assigned_team: SEED_TEAMS[0],
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440004',
    message_id: '550e8400-e29b-41d4-a716-446655440004',
    original_sender_device_id: 'NODE-CIVIL-204',
    gateway_device_id: 'NODE-GW-RELAY-3',
    category: 'Fire',
    description: 'Electrical transformer sparking with dry grass fire spreading near residential block.',
    latitude: 28.6320,
    longitude: 77.2250,
    accuracy_meters: 20.0,
    location_timestamp: new Date(Date.now() - 14400000).toISOString(),
    created_at: new Date(Date.now() - 14400000).toISOString(),
    expires_at: new Date(Date.now() + 172800000).toISOString(),
    received_at: new Date(Date.now() - 13000000).toISOString(),
    hop_count: 4,
    priority: 'HIGH',
    ingestion_status: 'ACCEPTED',
    operator_status: 'DISPATCHED',
    protocol_version: 1,
    payload_hash: '3344556677889900aabbccddeeff00112233445566778899aabbccddeeff0011',
    verification_status: 'VERIFIED',
    assigned_team_id: 'team-delta',
    assigned_team: SEED_TEAMS[3],
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440005',
    message_id: '550e8400-e29b-41d4-a716-446655440005',
    original_sender_device_id: 'NODE-CIVIL-301',
    gateway_device_id: 'NODE-GW-RELAY-2',
    category: 'Personal Safety',
    description: 'Power outage in Sector 7, suspected gas smell reported near school perimeter.',
    latitude: 28.5900,
    longitude: 77.2050,
    accuracy_meters: 30.0,
    location_timestamp: new Date(Date.now() - 28800000).toISOString(),
    created_at: new Date(Date.now() - 28800000).toISOString(),
    expires_at: new Date(Date.now() + 172800000).toISOString(),
    received_at: new Date(Date.now() - 28000000).toISOString(),
    hop_count: 2,
    priority: 'MODERATE',
    ingestion_status: 'ACCEPTED',
    operator_status: 'ACKNOWLEDGED',
    protocol_version: 1,
    payload_hash: '8899aabbccddeeff00112233445566778899aabbccddeeff0011223344556677',
    verification_status: 'UNDER_REVIEW',
  },
];

const SEED_WARNINGS: PublicWarning[] = [
  {
    id: 'warn-001',
    warning_code: 'WARN-2026-1042',
    title: 'CIVIL DEFENSE FLASH FLOOD WARNING',
    description: 'River Yamuna basin water levels have exceeded danger threshold by 1.8 meters. Rapid inundation in low-lying sectors anticipated.',
    category: 'Flood Warning',
    severity: 'SEVERE',
    affected_area: 'Yamuna Riverbank Sectors 4, 12, 18',
    issuing_authority: 'National Disaster Management Authority (NDMA)',
    status: 'PUBLISHED',
    instructions: 'Evacuate immediately to designated high-ground relief camps. Do not attempt to cross flooded roads or bridges.',
    created_at: new Date(Date.now() - 14400000).toISOString(),
    updated_at: new Date(Date.now() - 14400000).toISOString(),
    published_at: new Date(Date.now() - 14400000).toISOString(),
    expires_at: new Date(Date.now() + 86400000).toISOString(),
  },
  {
    id: 'warn-002',
    warning_code: 'WARN-2026-1043',
    title: 'HIGH VOLTAGE GRID FAILURE ADVISORY',
    description: 'Transformer explosion at Northern Substation. Grid power failure across northern residential districts. Standby mesh beacons active.',
    category: 'Infrastructure Advisory',
    severity: 'MODERATE',
    affected_area: 'Northern Municipal District',
    issuing_authority: 'State Emergency Operations Center',
    status: 'PUBLISHED',
    instructions: 'Switch to emergency radio frequencies. Report medical emergencies via BLACKOUT nearby SOS beacons.',
    created_at: new Date(Date.now() - 28800000).toISOString(),
    updated_at: new Date(Date.now() - 28800000).toISOString(),
    published_at: new Date(Date.now() - 28800000).toISOString(),
    expires_at: new Date(Date.now() + 86400000).toISOString(),
  },
];

const SEED_AUDIT: AuditLog[] = [
  {
    id: 'audit-001',
    actor_id: 'op-admin-blackout-ops',
    actor_email: 'admin@blackout.ops',
    actor_role: 'SUPER_ADMIN',
    action: 'SYSTEM_INITIALIZATION',
    resource_type: 'SYSTEM',
    resource_id: 'CORE-OPS-1',
    timestamp: new Date(Date.now() - 86400000).toISOString(),
    details: { environment: 'BLACKOUT Command Center', mode: 'OPERATIONAL' },
  },
  {
    id: 'audit-002',
    actor_id: 'op-admin-blackout-ops',
    actor_email: 'admin@blackout.ops',
    actor_role: 'SUPER_ADMIN',
    action: 'PUBLISH_WARNING',
    resource_type: 'PUBLIC_WARNING',
    resource_id: 'warn-001',
    timestamp: new Date(Date.now() - 14400000).toISOString(),
    details: { warningCode: 'WARN-2026-1042', severity: 'SEVERE' },
  },
  {
    id: 'audit-003',
    actor_id: 'op-operator-blackout-ops',
    actor_email: 'operator@blackout.ops',
    actor_role: 'CONTROL_ROOM_OPERATOR',
    action: 'INCIDENT_VERIFIED',
    resource_type: 'INCIDENT',
    resource_id: '550e8400-e29b-41d4-a716-446655440002',
    timestamp: new Date(Date.now() - 3000000).toISOString(),
    details: { confirmedPriority: 'CRITICAL', notes: 'Evacuation boat requested' },
  },
];

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
