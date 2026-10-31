import React, { useState } from 'react';
import { EmergencyAlert, VerificationStatus, EmergencyPriority } from '../../types/database';
import { incidentService } from '../../services/incidentService';
import { useAuth } from '../../hooks/useAuth';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { CheckCircle2, XCircle, Clock } from 'lucide-react';

interface VerificationModalProps {
  alert: EmergencyAlert | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const VerificationModal: React.FC<VerificationModalProps> = ({
  alert,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { user, profile } = useAuth();
  const [status, setStatus] = useState<VerificationStatus>('VERIFIED');
  const [priority, setPriority] = useState<EmergencyPriority>('HIGH');
  const [notes, setNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!alert) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;

    if (status === 'FALSE_REPORT' && !rejectionReason.trim()) {
      setError('A rejection reason is mandatory when declaring an alert as a FALSE REPORT.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      const res = await incidentService.updateVerification({
        alertId: alert.id,
        messageId: alert.message_id,
        newVerificationStatus: status,
        operatorNotes: notes.trim() || undefined,
        rejectionReason: status === 'FALSE_REPORT' ? rejectionReason.trim() : undefined,
        actor: {
          id: user.id,
          email: user.email || 'operator@blackout.ops',
          role: profile?.role || 'CONTROL_ROOM_OPERATOR',
        },
      });

      if (!res.success) throw new Error(res.error || 'Verification update failed.');

      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Failed to verify alert:', err);
      setError(err.message || 'Verification update failed.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Incident Verification & Assessment" maxWidth="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="p-3 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono">
            {error}
          </div>
        )}

        <div className="p-3 rounded bg-operations-bg border border-operations-border text-xs font-mono space-y-1">
          <div className="flex justify-between">
            <span className="text-operations-muted">Incident ID:</span>
            <span className="text-white font-bold">{alert.message_id.slice(0, 8)}...</span>
          </div>
          <div className="flex justify-between">
            <span className="text-operations-muted">Category:</span>
            <span className="text-operations-cyan">{alert.category}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-operations-muted">Initial Priority:</span>
            <span className="text-operations-amber">{alert.priority}</span>
          </div>
        </div>

        {/* Verification Status Choice */}
        <div>
          <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
            Verification Decision
          </label>
          <div className="grid grid-cols-3 gap-2">
            <button
              type="button"
              onClick={() => setStatus('VERIFIED')}
              className={`p-2.5 rounded border text-xs font-mono flex flex-col items-center justify-center space-y-1 transition-all ${
                status === 'VERIFIED'
                  ? 'bg-operations-green/10 border-operations-green text-operations-green font-bold'
                  : 'bg-operations-card border-operations-border text-operations-muted hover:border-operations-border/80'
              }`}
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>VERIFIED</span>
            </button>

            <button
              type="button"
              onClick={() => setStatus('UNDER_REVIEW')}
              className={`p-2.5 rounded border text-xs font-mono flex flex-col items-center justify-center space-y-1 transition-all ${
                status === 'UNDER_REVIEW'
                  ? 'bg-operations-amber/10 border-operations-amber text-operations-amber font-bold'
                  : 'bg-operations-card border-operations-border text-operations-muted hover:border-operations-border/80'
              }`}
            >
              <Clock className="w-4 h-4" />
              <span>REVIEWING</span>
            </button>

            <button
              type="button"
              onClick={() => setStatus('FALSE_REPORT')}
              className={`p-2.5 rounded border text-xs font-mono flex flex-col items-center justify-center space-y-1 transition-all ${
                status === 'FALSE_REPORT'
                  ? 'bg-operations-red/10 border-operations-red text-operations-red font-bold'
                  : 'bg-operations-card border-operations-border text-operations-muted hover:border-operations-border/80'
              }`}
            >
              <XCircle className="w-4 h-4" />
              <span>FALSE REPORT</span>
            </button>
          </div>
        </div>

        {/* Priority adjustment if verified */}
        {status === 'VERIFIED' && (
          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Confirmed Incident Priority
            </label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as EmergencyPriority)}
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            >
              <option value="CRITICAL">CRITICAL (Immediate Threat to Life)</option>
              <option value="HIGH">HIGH (Urgent Rescue Required)</option>
              <option value="MODERATE">MODERATE (Standard Escalation)</option>
              <option value="LOW">LOW (Informational / Stable)</option>
            </select>
          </div>
        )}

        {/* Rejection reason if false report */}
        {status === 'FALSE_REPORT' && (
          <div>
            <label className="block text-xs font-mono uppercase text-operations-red mb-1 font-semibold">
              Rejection Reason *
            </label>
            <input
              type="text"
              required
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              placeholder="e.g. Duplicate report, prank broadcast, verified cleared on scene"
              className="w-full bg-operations-bg border border-operations-red/50 rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-red"
            />
          </div>
        )}

        {/* Operator assessment notes */}
        <div>
          <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
            Operator Telemetry & Field Notes
          </label>
          <textarea
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Record dispatch findings, caller confirmations, or cross-checks..."
            className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          />
        </div>

        <div className="flex justify-end space-x-2 pt-2 border-t border-operations-border">
          <Button variant="ghost" type="button" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={isSubmitting}
            variant={status === 'FALSE_REPORT' ? 'danger' : 'primary'}
          >
            Commit Verification
          </Button>
        </div>
      </form>
    </Modal>
  );
};
