import React, { useState, useEffect } from 'react';
import { auditService } from '../services/auditService';
import { AuditLog } from '../types/database';
import { Button } from '../components/ui/Button';
import { exportAuditLogsToCsv } from '../utils/exportCsv';
import { formatDateTime, formatRelativeTime } from '../utils/formatters';
import {
  Download,
  RefreshCw,
  Terminal,
} from 'lucide-react';

export const AuditLogs: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionFilter, setActionFilter] = useState('');
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const loadLogs = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await auditService.getAuditLogs({
        limit: 100,
        action: actionFilter || undefined,
      });
      if (res.error) throw new Error(res.error);
      setLogs(res.data);
    } catch (err: any) {
      console.error('Failed to load audit logs:', err);
      setError(err.message || 'Error querying audit trail.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadLogs();
  }, [actionFilter]);

  const handleExport = () => {
    exportAuditLogsToCsv(logs);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>IMMUTABLE AUDIT TRAIL</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
              SECURITY LEDGER
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Tamper-evident record of all operator actions, verifications, dispatches, and logins
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <select
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
            className="bg-operations-surface border border-operations-border rounded px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="">All Audit Actions</option>
            <option value="USER_LOGIN">User Login</option>
            <option value="INCIDENT_VERIFICATION">Incident Verification</option>
            <option value="ASSIGN_RESPONSE_TEAM">Team Dispatched</option>
            <option value="CREATE_WARNING_DRAFT">Warning Created</option>
            <option value="PUBLISH_WARNING">Warning Published</option>
            <option value="REVOKE_WARNING">Warning Revoked</option>
          </select>

          <Button variant="ghost" size="sm" onClick={loadLogs} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>

          <Button variant="secondary" size="sm" onClick={handleExport} disabled={logs.length === 0}>
            <Download className="w-3.5 h-3.5 mr-1.5" />
            Export CSV
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono">
          {error}
        </div>
      )}

      {/* Audit Table */}
      <div className="bg-operations-surface border border-operations-border rounded overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="bg-operations-card/80 border-b border-operations-border text-[11px] uppercase tracking-wider text-operations-muted">
              <tr>
                <th className="py-3 px-4">Timestamp</th>
                <th className="py-3 px-4">Operator / Actor</th>
                <th className="py-3 px-4">Action</th>
                <th className="py-3 px-4">Target Resource</th>
                <th className="py-3 px-4 text-right">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-operations-border">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-operations-muted animate-pulse">
                    Querying Immutable Audit Ledger...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-operations-muted">
                    No audit records match the current filter.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr
                    key={log.id}
                    className="hover:bg-operations-card/50 transition-colors cursor-pointer"
                    onClick={() => setSelectedLog(log)}
                  >
                    <td className="py-3 px-4 whitespace-nowrap text-operations-muted">
                      <p className="text-white font-mono">{formatDateTime(log.timestamp)}</p>
                      <p className="text-[10px] text-slate-500">{formatRelativeTime(log.timestamp)}</p>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="space-y-0.5">
                        <p className="font-bold text-white">{log.actor_email || 'System / Auto'}</p>
                        <p className="text-[10px] text-operations-cyan">{log.actor_role || 'SYSTEM'}</p>
                      </div>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <span className="px-2 py-0.5 rounded bg-operations-bg border border-operations-border text-operations-text font-bold text-[10px]">
                        {log.action}
                      </span>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-operations-muted">
                      <span className="text-white">{log.resource_type}</span>
                      {log.resource_id && (
                        <span className="text-[10px] text-slate-500 block truncate max-w-[150px]">
                          ID: {log.resource_id}
                        </span>
                      )}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-right">
                      <Button variant="ghost" size="sm">
                        <Terminal className="w-3.5 h-3.5 text-operations-cyan" />
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Metadata Detail Inspector Drawer / Modal */}
      {selectedLog && (
        <div className="p-4 rounded bg-operations-card border border-operations-border space-y-3 font-mono text-xs">
          <div className="flex items-center justify-between pb-2 border-b border-operations-border">
            <span className="font-bold text-white uppercase flex items-center space-x-2">
              <Terminal className="w-4 h-4 text-operations-cyan" />
              <span>Audit Log Detail • ID: {selectedLog.id}</span>
            </span>
            <Button variant="ghost" size="sm" onClick={() => setSelectedLog(null)}>
              Close
            </Button>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-[11px]">
            <div className="p-2 rounded bg-operations-bg border border-operations-border">
              <span className="text-operations-muted block text-[10px]">ACTION</span>
              <span className="text-operations-cyan font-bold">{selectedLog.action}</span>
            </div>
            <div className="p-2 rounded bg-operations-bg border border-operations-border">
              <span className="text-operations-muted block text-[10px]">RESOURCE</span>
              <span className="text-white font-bold">{selectedLog.resource_type}</span>
            </div>
            <div className="p-2 rounded bg-operations-bg border border-operations-border">
              <span className="text-operations-muted block text-[10px]">ACTOR EMAIL</span>
              <span className="text-slate-300 truncate block">{selectedLog.actor_email || 'SYSTEM'}</span>
            </div>
            <div className="p-2 rounded bg-operations-bg border border-operations-border">
              <span className="text-operations-muted block text-[10px]">ACTOR ROLE</span>
              <span className="text-slate-300">{selectedLog.actor_role || 'SYSTEM'}</span>
            </div>
          </div>

          <div>
            <span className="text-[10px] uppercase text-operations-muted block mb-1 font-semibold">
              Raw Payload JSON
            </span>
            <pre className="p-3 rounded bg-operations-bg border border-operations-border text-[11px] text-operations-green overflow-x-auto">
              {JSON.stringify(selectedLog.details, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
