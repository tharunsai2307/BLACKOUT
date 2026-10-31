import React, { useState, useEffect } from 'react';
import { incidentService } from '../services/incidentService';
import { EmergencyAlert, EmergencyPriority, VerificationStatus } from '../types/database';
import { useRealtimeAlerts } from '../hooks/useRealtime';
import { EmergencyMap } from '../components/map/EmergencyMap';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { formatRelativeTime } from '../utils/formatters';
import { RefreshCw, MapPin } from 'lucide-react';
import { Link } from 'react-router-dom';

export const LiveMap: React.FC = () => {
  const [incidents, setIncidents] = useState<EmergencyAlert[]>([]);
  const [selectedIncident, setSelectedIncident] = useState<EmergencyAlert | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Filters
  const [priorityFilter, setPriorityFilter] = useState<EmergencyPriority | ''>('');
  const [statusFilter, setStatusFilter] = useState<VerificationStatus | ''>('');
  const [categoryFilter, setCategoryFilter] = useState('');

  const loadIncidents = async () => {
    try {
      setIsLoading(true);
      const res = await incidentService.getIncidents({
        limit: 100, // Fetch up to 100 recent incidents for spatial map view
        priority: priorityFilter || undefined,
        verificationStatus: statusFilter || undefined,
        category: categoryFilter || undefined,
      });
      setIncidents(res.data);
      if (res.data.length > 0 && !selectedIncident) {
        setSelectedIncident(res.data[0]);
      }
    } catch (err: any) {
      console.error('Failed to load map incidents:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, [priorityFilter, statusFilter, categoryFilter]);

  // Realtime updates
  useRealtimeAlerts((newAlert: EmergencyAlert) => {
    setIncidents((prev) => [newAlert, ...prev]);
  });

  return (
    <div className="h-[calc(100vh-6.5rem)] flex flex-col space-y-3">
      {/* Top Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded bg-operations-surface border border-operations-border">
        <div className="flex items-center space-x-2">
          <MapPin className="w-4 h-4 text-operations-cyan" />
          <span className="text-xs font-mono font-bold text-white uppercase tracking-wider">
            TACTICAL INCIDENT MAP
          </span>
          <span className="text-[10px] font-mono text-operations-muted">
            ({incidents.length} loaded)
          </span>
        </div>

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="bg-operations-bg border border-operations-border rounded px-2.5 py-1 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="">All Categories</option>
            <option value="Medical Emergency">Medical</option>
            <option value="Road Accident">Road Accident</option>
            <option value="Trapped / Stranded">Trapped</option>
            <option value="Flood">Flood</option>
            <option value="Fire">Fire</option>
            <option value="Natural Disaster">Natural Disaster</option>
          </select>

          <select
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value as EmergencyPriority)}
            className="bg-operations-bg border border-operations-border rounded px-2.5 py-1 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="">All Priorities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MODERATE">Moderate</option>
            <option value="LOW">Low</option>
          </select>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as VerificationStatus)}
            className="bg-operations-bg border border-operations-border rounded px-2.5 py-1 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="">All States</option>
            <option value="UNVERIFIED">Unverified</option>
            <option value="UNDER_REVIEW">Under Review</option>
            <option value="VERIFIED">Verified</option>
          </select>

          <Button variant="ghost" size="sm" onClick={loadIncidents} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {/* Main Split View: Map + Incidents Drawer */}
      <div className="flex-1 flex flex-col md:flex-row gap-3 min-h-0">
        {/* Full Interactive Map */}
        <div className="flex-1 h-full min-h-[350px]">
          <EmergencyMap
            incidents={incidents}
            selectedIncident={selectedIncident}
            onSelectIncident={(inc) => setSelectedIncident(inc)}
          />
        </div>

        {/* Side List of Visible Incidents */}
        <div className="w-full md:w-80 h-full bg-operations-surface border border-operations-border rounded flex flex-col overflow-hidden">
          <div className="p-3 bg-operations-card border-b border-operations-border flex items-center justify-between">
            <span className="text-xs font-mono font-bold uppercase tracking-wider text-white">
              Incidents Feed
            </span>
            <span className="text-[10px] font-mono text-operations-muted">
              Select to focus
            </span>
          </div>

          <div className="flex-1 overflow-y-auto divide-y divide-operations-border p-2 space-y-2">
            {incidents.length === 0 ? (
              <div className="py-12 text-center text-xs font-mono text-operations-muted">
                No incidents match filter criteria.
              </div>
            ) : (
              incidents.map((incident) => {
                const isSelected = selectedIncident?.message_id === incident.message_id;
                const hasGps =
                  incident.latitude != null &&
                  incident.longitude != null &&
                  !(incident.latitude === 0 && incident.longitude === 0);

                return (
                  <div
                    key={incident.message_id}
                    onClick={() => setSelectedIncident(incident)}
                    className={`p-3 rounded border cursor-pointer transition-all ${
                      isSelected
                        ? 'bg-operations-card border-operations-cyan shadow-sm shadow-operations-cyan/20'
                        : 'bg-operations-bg/60 border-operations-border hover:border-operations-border/80'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="text-xs font-bold font-mono text-white">
                        {incident.category}
                      </span>
                      <Badge variant={incident.priority} />
                    </div>

                    <p className="text-[11px] font-mono text-operations-muted mt-1 truncate">
                      {incident.description || 'No description provided.'}
                    </p>

                    <div className="mt-2 flex items-center justify-between text-[10px] font-mono">
                      <span className={hasGps ? 'text-operations-cyan' : 'text-operations-amber'}>
                        {hasGps ? 'GPS LOCKED' : 'NO GPS FIX'}
                      </span>
                      <span className="text-operations-muted">
                        {formatRelativeTime(incident.created_at)}
                      </span>
                    </div>

                    {isSelected && (
                      <div className="mt-2 pt-2 border-t border-operations-border/60 flex justify-end">
                        <Link
                          to={`/incidents/${incident.message_id}`}
                          className="text-[10px] font-mono text-operations-cyan hover:underline"
                        >
                          Open Detailed View →
                        </Link>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
