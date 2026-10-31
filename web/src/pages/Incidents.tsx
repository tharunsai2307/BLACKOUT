import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentService, IncidentFilterOptions } from '../services/incidentService';
import { EmergencyAlert, VerificationStatus, EmergencyPriority } from '../types/database';
import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { VerificationModal } from '../components/incidents/VerificationModal';
import { AssignTeamModal } from '../components/incidents/AssignTeamModal';
import { exportIncidentsToCsv } from '../utils/exportCsv';
import { formatCoordinates, formatRelativeTime, formatDateTime } from '../utils/formatters';
import {
  Search,
  Download,
  CheckCircle2,
  Truck,
  Eye,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

export const Incidents: React.FC = () => {
  const { canVerify, canDispatch } = useAuth();

  const [incidents, setIncidents] = useState<EmergencyAlert[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(1);
  const [limit] = useState(15);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filter States
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState<EmergencyPriority | ''>('');
  const [statusFilter, setStatusFilter] = useState<VerificationStatus | ''>('');

  // Modals
  const [verificationAlert, setVerificationAlert] = useState<EmergencyAlert | null>(null);
  const [assignAlert, setAssignAlert] = useState<EmergencyAlert | null>(null);

  const loadIncidents = async () => {
    try {
      setIsLoading(true);
      setError(null);

      const filter: IncidentFilterOptions = {
        limit,
        offset: (page - 1) * limit,
        search: searchTerm.trim() || undefined,
        category: categoryFilter || undefined,
        priority: priorityFilter || undefined,
        verificationStatus: statusFilter || undefined,
      };

      const result = await incidentService.getIncidents(filter);
      if (result.error) throw new Error(result.error);

      setIncidents(result.data);
      setTotalCount(result.count);
    } catch (err: any) {
      console.error('Failed to load incidents:', err);
      setError(err.message || 'Error fetching incidents from central database.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, [page, categoryFilter, priorityFilter, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    loadIncidents();
  };

  const handleExportCsv = () => {
    exportIncidentsToCsv(incidents);
  };

  const totalPages = Math.ceil(totalCount / limit) || 1;

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>INCIDENT MANAGEMENT</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
              {totalCount} TOTAL RECORDS
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Filter, verify, and dispatch emergency response units to incoming mesh alerts
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <Button variant="ghost" size="sm" onClick={loadIncidents} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button variant="secondary" size="sm" onClick={handleExportCsv} disabled={incidents.length === 0}>
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

      {/* Filter and Search Controls */}
      <Card className="p-4">
        <form onSubmit={handleSearchSubmit} className="grid grid-cols-1 md:grid-cols-5 gap-3">
          {/* Search Box */}
          <div className="md:col-span-2 relative">
            <Search className="w-4 h-4 text-operations-muted absolute left-3 top-3" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search by ID, device ID, gateway, or text..."
              className="w-full bg-operations-bg border border-operations-border rounded p-2 pl-9 text-xs font-mono text-white placeholder-slate-600 focus:outline-none focus:border-operations-cyan"
            />
          </div>

          {/* Category Filter */}
          <div>
            <select
              value={categoryFilter}
              onChange={(e) => {
                setCategoryFilter(e.target.value);
                setPage(1);
              }}
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            >
              <option value="">All Categories</option>
              <option value="Medical Emergency">Medical Emergency</option>
              <option value="Road Accident">Road Accident</option>
              <option value="Trapped / Stranded">Trapped / Stranded</option>
              <option value="Flood">Flood</option>
              <option value="Fire">Fire</option>
              <option value="Natural Disaster">Natural Disaster</option>
              <option value="Personal Safety">Personal Safety</option>
              <option value="Other">Other</option>
            </select>
          </div>

          {/* Priority Filter */}
          <div>
            <select
              value={priorityFilter}
              onChange={(e) => {
                setPriorityFilter(e.target.value as EmergencyPriority);
                setPage(1);
              }}
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            >
              <option value="">All Priorities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MODERATE">Moderate</option>
              <option value="LOW">Low</option>
            </select>
          </div>

          {/* Verification Status Filter */}
          <div>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as VerificationStatus);
                setPage(1);
              }}
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            >
              <option value="">All Verification States</option>
              <option value="UNVERIFIED">Unverified</option>
              <option value="UNDER_REVIEW">Under Review</option>
              <option value="VERIFIED">Verified</option>
              <option value="FALSE_REPORT">False Report</option>
            </select>
          </div>
        </form>
      </Card>

      {/* Incidents Table */}
      <div className="bg-operations-surface border border-operations-border rounded overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="bg-operations-card/80 border-b border-operations-border text-[11px] uppercase tracking-wider text-operations-muted">
              <tr>
                <th className="py-3 px-4">Priority / Status</th>
                <th className="py-3 px-4">Incident ID & Category</th>
                <th className="py-3 px-4">Location & Coordinates</th>
                <th className="py-3 px-4">Telemetry & Hops</th>
                <th className="py-3 px-4">Ingested At</th>
                <th className="py-3 px-4 text-right">Operations Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-operations-border">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-operations-muted animate-pulse">
                    Querying Incident Records from Central Database...
                  </td>
                </tr>
              ) : incidents.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-operations-muted">
                    No emergency incidents match current query criteria.
                  </td>
                </tr>
              ) : (
                incidents.map((incident) => (
                  <tr
                    key={incident.message_id}
                    className="hover:bg-operations-card/50 transition-colors group"
                  >
                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="flex flex-col space-y-1">
                        <Badge variant={incident.priority} />
                        <Badge variant={incident.verification_status} />
                      </div>
                    </td>

                    <td className="py-3 px-4">
                      <div className="space-y-0.5">
                        <Link
                          to={`/incidents/${incident.message_id}`}
                          className="font-bold text-white group-hover:text-operations-cyan transition-colors"
                        >
                          {incident.category}
                        </Link>
                        <p className="text-[10px] text-operations-muted">
                          ID: {incident.message_id.slice(0, 8)}...
                        </p>
                        {incident.description && (
                          <p className="text-[11px] text-slate-300 max-w-xs truncate">
                            {incident.description}
                          </p>
                        )}
                      </div>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="text-[11px] text-operations-muted space-y-0.5">
                        <p className="text-white font-mono">
                          {formatCoordinates(incident.latitude, incident.longitude, incident.accuracy_meters)}
                        </p>
                        <p className="text-[10px]">
                          Accuracy: {incident.accuracy_meters != null ? `±${Math.round(incident.accuracy_meters)}m` : 'N/A'}
                        </p>
                      </div>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="text-[10px] text-operations-muted space-y-0.5">
                        <p>Hops: <strong className="text-white">{incident.hop_count}</strong></p>
                        <p>Origin: {incident.original_sender_device_id.slice(0, 6)}</p>
                        <p>Gateway: {incident.gateway_device_id.slice(0, 6)}</p>
                      </div>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-operations-muted text-[11px]">
                      <p>{formatRelativeTime(incident.created_at)}</p>
                      <p className="text-[10px] text-slate-500">{formatDateTime(incident.created_at)}</p>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-right">
                      <div className="flex items-center justify-end space-x-1.5">
                        {canVerify() && (
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Verify Incident"
                            onClick={() => setVerificationAlert(incident)}
                          >
                            <CheckCircle2 className="w-3.5 h-3.5 text-operations-cyan" />
                          </Button>
                        )}

                        {canDispatch() && (
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Dispatch Unit"
                            onClick={() => setAssignAlert(incident)}
                          >
                            <Truck className="w-3.5 h-3.5 text-operations-amber" />
                          </Button>
                        )}

                        <Link to={`/incidents/${incident.message_id}`}>
                          <Button variant="ghost" size="sm" title="View Full Telemetry">
                            <Eye className="w-3.5 h-3.5 text-white" />
                          </Button>
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="p-3 bg-operations-card border-t border-operations-border flex items-center justify-between text-xs font-mono">
          <span className="text-operations-muted">
            Page <strong className="text-white">{page}</strong> of <strong className="text-white">{totalPages}</strong>
          </span>

          <div className="flex items-center space-x-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={page <= 1 || isLoading}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              <ChevronLeft className="w-3.5 h-3.5 mr-1" />
              Previous
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={page >= totalPages || isLoading}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              Next
              <ChevronRight className="w-3.5 h-3.5 ml-1" />
            </Button>
          </div>
        </div>
      </div>

      {/* Verification Modal */}
      <VerificationModal
        alert={verificationAlert}
        isOpen={!!verificationAlert}
        onClose={() => setVerificationAlert(null)}
        onSuccess={() => {
          loadIncidents();
        }}
      />

      {/* Assign Team Modal */}
      <AssignTeamModal
        alert={assignAlert}
        isOpen={!!assignAlert}
        onClose={() => setAssignAlert(null)}
        onSuccess={() => {
          loadIncidents();
        }}
      />
    </div>
  );
};
