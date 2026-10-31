import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { incidentService } from '../services/incidentService';
import { EmergencyAlert } from '../types/database';
import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { EmergencyMap } from '../components/map/EmergencyMap';
import { VerificationModal } from '../components/incidents/VerificationModal';
import { AssignTeamModal } from '../components/incidents/AssignTeamModal';
import { formatCoordinates, formatDateTime, formatRelativeTime } from '../utils/formatters';
import {
  ArrowLeft,
  CheckCircle2,
  Truck,
  RefreshCw,
  Hash,
} from 'lucide-react';

export const IncidentDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { canVerify, canDispatch } = useAuth();

  const [alert, setAlert] = useState<EmergencyAlert | null>(null);
  const [events, setEvents] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [isVerifyOpen, setIsVerifyOpen] = useState(false);
  const [isAssignOpen, setIsAssignOpen] = useState(false);

  const loadDetail = async () => {
    if (!id) return;
    try {
      setIsLoading(true);
      setError(null);
      const res = await incidentService.getIncidentById(id);
      if (res.error) throw new Error(res.error);
      setAlert(res.alert);
      setEvents(res.events);
    } catch (err: any) {
      console.error('Failed to load incident detail:', err);
      setError(err.message || 'Error querying incident record.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDetail();
  }, [id]);

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center text-xs font-mono text-operations-cyan animate-pulse">
        <RefreshCw className="w-6 h-6 animate-spin mr-2" />
        Retrieving Incident Cryptographic Telemetry...
      </div>
    );
  }

  if (error || !alert) {
    return (
      <div className="p-6 text-center space-y-4">
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red font-mono text-xs max-w-md mx-auto">
          {error || 'Incident record not found in central database.'}
        </div>
        <Button variant="secondary" size="sm" onClick={() => navigate('/incidents')}>
          <ArrowLeft className="w-3.5 h-3.5 mr-1" />
          Return to Incidents
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Back button & Primary actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div className="flex items-center space-x-3">
          <Button variant="ghost" size="sm" onClick={() => navigate('/incidents')}>
            <ArrowLeft className="w-3.5 h-3.5 mr-1" />
            Back
          </Button>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-lg font-bold font-mono text-white">
                INCIDENT TELEMETRY
              </h1>
              <span className="text-xs font-mono text-operations-cyan bg-operations-card px-2 py-0.5 rounded border border-operations-cyan/30">
                {alert.message_id}
              </span>
            </div>
            <p className="text-[11px] text-operations-muted font-mono mt-0.5">
              Ingested {formatDateTime(alert.created_at)} ({formatRelativeTime(alert.created_at)})
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          {canVerify() && (
            <Button variant="primary" size="sm" onClick={() => setIsVerifyOpen(true)}>
              <CheckCircle2 className="w-3.5 h-3.5 mr-1.5" />
              Verify / Review
            </Button>
          )}

          {canDispatch() && (
            <Button variant="secondary" size="sm" onClick={() => setIsAssignOpen(true)}>
              <Truck className="w-3.5 h-3.5 mr-1.5 text-operations-amber" />
              Dispatch Team
            </Button>
          )}
        </div>
      </div>

      {/* Top Status & Overview Row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card className="border-l-4 border-l-operations-cyan">
          <span className="text-[10px] font-mono uppercase text-operations-muted">Category</span>
          <p className="text-sm font-bold font-mono text-white mt-1">{alert.category}</p>
        </Card>

        <Card className="border-l-4 border-l-operations-red">
          <span className="text-[10px] font-mono uppercase text-operations-muted">Priority</span>
          <div className="mt-1">
            <Badge variant={alert.priority} />
          </div>
        </Card>

        <Card className="border-l-4 border-l-operations-amber">
          <span className="text-[10px] font-mono uppercase text-operations-muted">Verification Status</span>
          <div className="mt-1">
            <Badge variant={alert.verification_status} />
          </div>
        </Card>

        <Card className="border-l-4 border-l-operations-green">
          <span className="text-[10px] font-mono uppercase text-operations-muted">Assigned Team</span>
          <p className="text-xs font-mono font-bold text-white mt-1">
            {alert.assigned_team?.name || 'UNASSIGNED'}
          </p>
        </Card>
      </div>

      {/* Main Grid: Details + Map */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column: Telemetry & Notes */}
        <div className="space-y-6">
          {/* Core Emergency Payload Card */}
          <Card title="Emergency Payload Telemetry">
            <div className="space-y-3 font-mono text-xs">
              <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
                <span className="text-[10px] uppercase text-operations-muted">Reported Emergency Description</span>
                <p className="text-white text-xs whitespace-pre-wrap">
                  {alert.description || 'No description attached with SOS transmission.'}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 text-[11px]">
                <div className="p-2.5 rounded bg-operations-bg border border-operations-border">
                  <span className="text-operations-muted block text-[10px]">ORIGIN SENDER DEVICE</span>
                  <span className="text-white font-bold">{alert.original_sender_device_id}</span>
                </div>

                <div className="p-2.5 rounded bg-operations-bg border border-operations-border">
                  <span className="text-operations-muted block text-[10px]">INGESTION GATEWAY DEVICE</span>
                  <span className="text-white font-bold">{alert.gateway_device_id}</span>
                </div>

                <div className="p-2.5 rounded bg-operations-bg border border-operations-border">
                  <span className="text-operations-muted block text-[10px]">MESH HOP COUNT</span>
                  <span className="text-operations-cyan font-bold">{alert.hop_count} hops</span>
                </div>

                <div className="p-2.5 rounded bg-operations-bg border border-operations-border">
                  <span className="text-operations-muted block text-[10px]">LOCATION ACCURACY</span>
                  <span className="text-white font-bold">
                    {alert.accuracy_meters != null ? `±${Math.round(alert.accuracy_meters)} meters` : 'Unknown'}
                  </span>
                </div>
              </div>

              {alert.payload_hash && (
                <div className="p-2.5 rounded bg-operations-bg border border-operations-border flex items-center space-x-2 text-[10px]">
                  <Hash className="w-3.5 h-3.5 text-operations-muted flex-shrink-0" />
                  <span className="text-operations-muted truncate">Hash: {alert.payload_hash}</span>
                </div>
              )}
            </div>
          </Card>

          {/* Verification & Operator Assessment Notes */}
          <Card title="Operations Assessment & Operator Notes">
            <div className="space-y-3 font-mono text-xs">
              <div className="flex justify-between items-center text-[11px] pb-2 border-b border-operations-border">
                <span className="text-operations-muted">Verification Status:</span>
                <Badge variant={alert.verification_status} />
              </div>

              {alert.verified_at && (
                <div className="flex justify-between items-center text-[11px] pb-2 border-b border-operations-border">
                  <span className="text-operations-muted">Verified Timestamp:</span>
                  <span className="text-slate-300">{formatDateTime(alert.verified_at)}</span>
                </div>
              )}

              {alert.rejection_reason && (
                <div className="p-3 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs space-y-1">
                  <span className="text-[10px] uppercase font-bold">False Report Justification</span>
                  <p>{alert.rejection_reason}</p>
                </div>
              )}

              <div className="p-3 rounded bg-operations-bg border border-operations-border space-y-1">
                <span className="text-[10px] uppercase text-operations-muted">Field / Control Room Notes</span>
                <p className="text-slate-200">
                  {alert.operator_notes || 'No operator assessment notes logged yet.'}
                </p>
              </div>
            </div>
          </Card>
        </div>

        {/* Right Column: Tactical Map & Events History */}
        <div className="space-y-6">
          {/* Tactical Incident Mini-Map */}
          <Card title="Geospatial Target Coordinate">
            <div className="h-64 w-full">
              <EmergencyMap incidents={[alert]} selectedIncident={alert} />
            </div>
            <div className="mt-3 flex items-center justify-between text-xs font-mono text-operations-muted">
              <span>{formatCoordinates(alert.latitude, alert.longitude, alert.accuracy_meters)}</span>
            </div>
          </Card>

          {/* Incident Audit & History Ledger */}
          <Card title="Incident Audit Trail">
            {events.length === 0 ? (
              <div className="py-6 text-center text-xs font-mono text-operations-muted">
                No operator actions logged for this incident yet.
              </div>
            ) : (
              <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                {events.map((event) => (
                  <div
                    key={event.id}
                    className="p-2.5 rounded bg-operations-bg border border-operations-border text-[11px] font-mono space-y-1"
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-operations-cyan">{event.event_type}</span>
                      <span className="text-operations-muted text-[10px]">
                        {formatRelativeTime(event.event_timestamp)}
                      </span>
                    </div>
                    <p className="text-slate-300">
                      Actor: {event.actor_id || 'System'}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>

      {/* Verification Modal */}
      <VerificationModal
        alert={alert}
        isOpen={isVerifyOpen}
        onClose={() => setIsVerifyOpen(false)}
        onSuccess={loadDetail}
      />

      {/* Assign Team Modal */}
      <AssignTeamModal
        alert={alert}
        isOpen={isAssignOpen}
        onClose={() => setIsAssignOpen(false)}
        onSuccess={loadDetail}
      />
    </div>
  );
};
