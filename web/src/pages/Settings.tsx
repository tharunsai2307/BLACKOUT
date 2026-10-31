import React from 'react';
import { useAuth } from '../hooks/useAuth';
import { useRealtimeStatus } from '../hooks/useRealtime';
import { Card } from '../components/ui/Card';
import { Database, Wifi, Server } from 'lucide-react';

export const Settings: React.FC = () => {
  const { profile, user } = useAuth();
  const realtimeStatus = useRealtimeStatus();

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Top Header */}
      <div className="pb-2 border-b border-operations-border/60">
        <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
          <span>OPERATIONS CONSOLE CONFIGURATION</span>
        </h1>
        <p className="text-xs text-operations-muted font-mono mt-0.5">
          Station security policies, authenticated operator credentials, and backend connectivity
        </p>
      </div>

      {/* Operator Profile Card */}
      <Card title="Active Operator Credentials">
        <div className="space-y-4 font-mono text-xs">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
              <span className="text-[10px] uppercase text-operations-muted">OPERATOR FULL NAME</span>
              <p className="text-white font-bold">{profile?.full_name || 'N/A'}</p>
            </div>

            <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
              <span className="text-[10px] uppercase text-operations-muted">AUTHENTICATED EMAIL</span>
              <p className="text-white font-bold">{user?.email || 'N/A'}</p>
            </div>

            <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
              <span className="text-[10px] uppercase text-operations-muted">GOVERNMENT AGENCY / ORG</span>
              <p className="text-operations-cyan font-bold">{profile?.organization || 'NDMA Operations'}</p>
            </div>

            <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
              <span className="text-[10px] uppercase text-operations-muted">ASSIGNED RBAC ROLE</span>
              <p className="text-operations-green font-bold">{profile?.role || 'CONTROL_ROOM_OPERATOR'}</p>
            </div>
          </div>
        </div>
      </Card>

      {/* Backend Infrastructure Status */}
      <Card title="Central Disaster Infrastructure Status">
        <div className="space-y-3 font-mono text-xs">
          <div className="flex items-center justify-between p-3 rounded bg-operations-bg border border-operations-border">
            <div className="flex items-center space-x-3">
              <Database className="w-5 h-5 text-operations-cyan" />
              <div>
                <p className="font-bold text-white">Supabase PostgreSQL 15</p>
                <p className="text-[10px] text-operations-muted">Central Emergency Cloud Store & Row-Level Security</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-operations-green/10 text-operations-green border border-operations-green/30 text-[10px] font-bold">
              OPERATIONAL
            </span>
          </div>

          <div className="flex items-center justify-between p-3 rounded bg-operations-bg border border-operations-border">
            <div className="flex items-center space-x-3">
              <Wifi className="w-5 h-5 text-operations-green" />
              <div>
                <p className="font-bold text-white">Realtime Broadcast WebSockets</p>
                <p className="text-[10px] text-operations-muted">Instant push notification for incoming mesh SOS</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30 text-[10px] font-bold">
              STATUS: {realtimeStatus}
            </span>
          </div>

          <div className="flex items-center justify-between p-3 rounded bg-operations-bg border border-operations-border">
            <div className="flex items-center space-x-3">
              <Server className="w-5 h-5 text-operations-amber" />
              <div>
                <p className="font-bold text-white">Mesh Gateway Ingestion API</p>
                <p className="text-[10px] text-operations-muted">Stores-Carry-Forward SOS Sync Endpoint</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-operations-card text-operations-muted border border-operations-border text-[10px] font-bold">
              /functions/v1/sos_ingest
            </span>
          </div>
        </div>
      </Card>

      {/* RBAC Authorization Matrix */}
      <Card title="Role-Based Security Matrix (Phase 6 Specs)">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="bg-operations-bg text-[10px] uppercase text-operations-muted">
              <tr>
                <th className="p-2">Role</th>
                <th className="p-2">View Incidents</th>
                <th className="p-2">Verify / Reject</th>
                <th className="p-2">Dispatch Teams</th>
                <th className="p-2">Public Warnings</th>
                <th className="p-2">Audit Logs</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-operations-border text-[11px]">
              <tr>
                <td className="p-2 font-bold text-operations-cyan">SUPER_ADMIN</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
              </tr>
              <tr>
                <td className="p-2 font-bold text-white">CONTROL_ROOM_OPERATOR</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-green">✓ Full</td>
                <td className="p-2 text-operations-muted">Draft only</td>
                <td className="p-2 text-operations-green">✓ View</td>
              </tr>
              <tr>
                <td className="p-2 font-bold text-white">FIELD_RESPONSE_COORDINATOR</td>
                <td className="p-2 text-operations-green">✓ View</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-green">✓ Update Status</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-muted">No</td>
              </tr>
              <tr>
                <td className="p-2 font-bold text-white">PUBLIC_WARNING_PUBLISHER</td>
                <td className="p-2 text-operations-green">✓ View</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-green">✓ Publish / Revoke</td>
                <td className="p-2 text-operations-muted">No</td>
              </tr>
              <tr>
                <td className="p-2 font-bold text-operations-muted">VIEWER</td>
                <td className="p-2 text-operations-green">✓ View</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-muted">No</td>
                <td className="p-2 text-operations-muted">No</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
