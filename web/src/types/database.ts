export type AppRole =
  | 'SUPER_ADMIN'
  | 'CONTROL_ROOM_OPERATOR'
  | 'FIELD_RESPONSE_COORDINATOR'
  | 'PUBLIC_WARNING_PUBLISHER'
  | 'VIEWER'
  | 'CITIZEN';

export type EmergencyPriority = 'CRITICAL' | 'HIGH' | 'MODERATE' | 'LOW';

export type IngestionStatus = 'PENDING' | 'ACCEPTED' | 'DUPLICATE_IGNORED' | 'REJECTED';

export type OperatorStatus =
  | 'NEW'
  | 'ACKNOWLEDGED'
  | 'TRIAGED'
  | 'DISPATCHED'
  | 'RESOLVED'
  | 'CLOSED';

export type VerificationStatus =
  | 'UNVERIFIED'
  | 'UNDER_REVIEW'
  | 'VERIFIED'
  | 'FALSE_REPORT';

export type TeamStatus =
  | 'AVAILABLE'
  | 'ASSIGNED'
  | 'EN_ROUTE'
  | 'ON_SCENE'
  | 'COMPLETED'
  | 'UNAVAILABLE';

export type TeamType =
  | 'SEARCH_AND_RESCUE'
  | 'MEDICAL_RESPONSE'
  | 'EVACUATION_CREW'
  | 'FIREFIGHTING'
  | 'HAZMAT'
  | 'DISASTER_LOGISTICS'
  | 'GENERAL';

export type WarningSeverity =
  | 'IMMEDIATE THREAT'
  | 'SEVERE'
  | 'MODERATE'
  | 'ADVISORY';

export type WarningStatus =
  | 'DRAFT'
  | 'SUBMITTED_FOR_APPROVAL'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'EXPIRED'
  | 'REVOKED';

export type EmergencyCategory =
  | 'Medical Emergency'
  | 'Road Accident'
  | 'Trapped / Stranded'
  | 'Flood'
  | 'Fire'
  | 'Natural Disaster'
  | 'Personal Safety'
  | 'Other';

export interface UserProfile {
  id: string;
  email: string;
  full_name: string;
  role: AppRole;
  badge_number?: string | null;
  organization: string;
  is_active: boolean;
  phone?: string | null;
  blood_type?: string | null;
  country?: string | null;
  preferred_language?: string | null;
  medical_notes?: string | null;
  created_at: string;
  updated_at: string;
}

export interface EmergencyAlert {
  id: string;
  message_id: string;
  original_sender_device_id: string;
  gateway_device_id: string;
  category: EmergencyCategory;
  description: string;
  latitude: number | null;
  longitude: number | null;
  accuracy_meters: number | null;
  location_timestamp: string | null;
  created_at: string;
  expires_at: string;
  received_at: string;
  hop_count: number;
  priority: EmergencyPriority;
  ingestion_status: IngestionStatus;
  operator_status: OperatorStatus;
  protocol_version: number;
  payload_hash: string;
  // Phase 9 Add-on Geoapify Location Enrichment
  geocoded_address?: string | null;
  geocoded_locality?: string | null;
  geocoded_district?: string | null;
  geocoded_state?: string | null;
  geocoded_country?: string | null;
  geocoded_postal_code?: string | null;
  geocoded_is_approximate?: boolean;
  geocoded_confidence?: number | null;
  geocoded_at?: string | null;
  // Phase 6 Extensions
  verification_status: VerificationStatus;
  assigned_team_id?: string | null;
  assigned_team?: ResponseTeam | null;
  verified_at?: string | null;
  verified_by?: string | null;
  operator_notes?: string | null;
  rejection_reason?: string | null;
  resolved_at?: string | null;
  resolved_by?: string | null;
  updated_at?: string | null;
}

export interface ResponseTeam {
  id: string;
  name: string;
  callsign: string;
  team_type: TeamType;
  status: TeamStatus;
  contact_phone?: string | null;
  base_location?: string | null;
  capacity: number;
  current_latitude?: number | null;
  current_longitude?: number | null;
  active_incident_id?: string | null;
  created_at: string;
  updated_at: string;
  members?: ResponseTeamMember[];
}

export interface ResponseTeamMember {
  id: string;
  team_id: string;
  full_name: string;
  role_title: string;
  phone?: string | null;
  badge_number?: string | null;
  is_team_lead: boolean;
  created_at: string;
}

export interface PublicWarning {
  id: string;
  warning_code: string;
  title: string;
  description: string;
  category: string;
  severity: WarningSeverity;
  affected_area: string;
  latitude?: number | null;
  longitude?: number | null;
  radius_km?: number | null;
  issuing_authority: string;
  status: WarningStatus;
  instructions?: string | null;
  created_by?: string | null;
  approved_by?: string | null;
  published_at?: string | null;
  expires_at: string;
  revoked_at?: string | null;
  revocation_reason?: string | null;
  created_at: string;
  updated_at: string;
}

export interface AuditLog {
  id: string;
  actor_id?: string | null;
  actor_email: string;
  actor_role: string;
  action: string;
  resource_type: string;
  resource_id: string;
  timestamp: string;
  details: Record<string, any>;
}

export interface GatewayUploadHistory {
  id: string;
  message_id: string;
  gateway_device_id: string;
  upload_attempted_at: string;
  upload_completed_at: string;
  upload_status: 'SUCCESS' | 'DUPLICATE' | 'REJECTED' | 'FAILED';
  error_code?: string | null;
  response_reference: string;
}

export interface EmergencyContact {
  id: string;
  user_id: string;
  contact_name: string;
  contact_phone: string;
  relationship: string;
  is_primary: boolean;
  created_at: string;
  updated_at: string;
}
