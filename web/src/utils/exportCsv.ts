import { EmergencyAlert, AuditLog } from '../types/database';

export function exportToCsv(filename: string, rows: Record<string, any>[]): void {
  if (!rows || rows.length === 0) {
    alert('No data available to export.');
    return;
  }

  const separator = ',';
  const keys = Object.keys(rows[0]);
  const header = keys.map((key) => `"${key}"`).join(separator);

  const csvRows = rows.map((row) => {
    return keys
      .map((key) => {
        let val = row[key];
        if (val === null || val === undefined) {
          val = '';
        } else if (typeof val === 'object') {
          val = JSON.stringify(val);
        }
        const stringVal = String(val).replace(/"/g, '""');
        return `"${stringVal}"`;
      })
      .join(separator);
  });

  const csvContent = [header, ...csvRows].join('\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `${filename}_${new Date().toISOString().split('T')[0]}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export function exportIncidentsToCsv(incidents: EmergencyAlert[]): void {
  const rows = incidents.map((inc) => ({
    message_id: inc.message_id,
    category: inc.category,
    priority: inc.priority,
    verification_status: inc.verification_status,
    operator_status: inc.operator_status,
    latitude: inc.latitude ?? '',
    longitude: inc.longitude ?? '',
    accuracy_meters: inc.accuracy_meters ?? '',
    hop_count: inc.hop_count,
    original_sender: inc.original_sender_device_id,
    gateway_device: inc.gateway_device_id,
    created_at: inc.created_at,
    description: inc.description || '',
    operator_notes: inc.operator_notes || '',
  }));

  exportToCsv('blackout_incidents_export', rows);
}

export function exportAuditLogsToCsv(logs: AuditLog[]): void {
  const rows = logs.map((log) => ({
    timestamp: log.timestamp,
    actor_email: log.actor_email,
    actor_role: log.actor_role,
    action: log.action,
    resource_type: log.resource_type,
    resource_id: log.resource_id,
    details: JSON.stringify(log.details),
  }));

  exportToCsv('blackout_audit_ledger', rows);
}
