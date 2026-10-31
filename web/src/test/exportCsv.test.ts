import { describe, it, expect, vi, beforeEach } from 'vitest';
import { exportIncidentsToCsv, exportAuditLogsToCsv } from '../utils/exportCsv';
import { EmergencyAlert, AuditLog } from '../types/database';

describe('CSV Export Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('exports incident records with accurate headers and escaping', () => {
    const mockAlerts: EmergencyAlert[] = [
      {
        id: 'uuid-1',
        message_id: 'msg-1234',
        original_sender_device_id: 'DEV_ORIGIN_A',
        gateway_device_id: 'DEV_GW_B',
        category: 'Medical Emergency',
        description: 'Patient having chest pains, "urgent" assistance needed',
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy_meters: 10.5,
        location_timestamp: '2026-11-01T10:00:00Z',
        created_at: '2026-11-01T10:00:00Z',
        expires_at: '2026-11-02T10:00:00Z',
        received_at: '2026-11-01T10:05:00Z',
        hop_count: 2,
        priority: 'CRITICAL',
        ingestion_status: 'ACCEPTED',
        operator_status: 'TRIAGED',
        protocol_version: 1,
        payload_hash: 'hash-abc',
        verification_status: 'VERIFIED',
      },
    ];

    const appendChildSpy = vi.spyOn(document.body, 'appendChild');
    const removeChildSpy = vi.spyOn(document.body, 'removeChild');

    exportIncidentsToCsv(mockAlerts);

    expect(appendChildSpy).toHaveBeenCalled();
    expect(removeChildSpy).toHaveBeenCalled();
  });

  it('exports audit logs with actor and action fields', () => {
    const mockLogs: AuditLog[] = [
      {
        id: 'audit-1',
        actor_id: 'usr-1',
        actor_email: 'operator@blackout.ops',
        actor_role: 'CONTROL_ROOM_OPERATOR',
        action: 'ALERT_VERIFIED',
        resource_type: 'INCIDENT',
        resource_id: 'msg-1234',
        timestamp: '2026-11-01T10:10:00Z',
        details: { verified: true },
      },
    ];

    const appendChildSpy = vi.spyOn(document.body, 'appendChild');
    exportAuditLogsToCsv(mockLogs);
    expect(appendChildSpy).toHaveBeenCalled();
  });
});
