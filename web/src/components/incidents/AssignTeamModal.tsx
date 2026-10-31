import React, { useState, useEffect } from 'react';
import { EmergencyAlert, ResponseTeam } from '../../types/database';
import { teamService } from '../../services/teamService';
import { incidentService } from '../../services/incidentService';
import { useAuth } from '../../hooks/useAuth';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { Truck } from 'lucide-react';

interface AssignTeamModalProps {
  alert: EmergencyAlert | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const AssignTeamModal: React.FC<AssignTeamModalProps> = ({
  alert,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { user, profile } = useAuth();
  const [teams, setTeams] = useState<ResponseTeam[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<string>('');
  const [updateTeamStatus, setUpdateTeamStatus] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      loadTeams();
    }
  }, [isOpen]);

  const loadTeams = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await teamService.getResponseTeams();
      if (res.error) throw new Error(res.error);
      setTeams(res.data);
      if (res.data.length > 0) {
        setSelectedTeamId(res.data[0].id);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load response teams.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!alert) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !selectedTeamId) return;

    try {
      setIsSubmitting(true);
      setError(null);

      const targetTeam = teams.find((t) => t.id === selectedTeamId);
      const actor = {
        id: user.id,
        email: user.email || 'operator@blackout.ops',
        role: profile?.role || 'CONTROL_ROOM_OPERATOR',
      };

      // 1. Assign team to incident
      const assignRes = await incidentService.assignTeam({
        alertId: alert.id,
        messageId: alert.message_id,
        teamId: selectedTeamId,
        teamName: targetTeam?.name || 'Response Unit',
        actor,
      });

      if (!assignRes.success) throw new Error(assignRes.error || 'Failed to dispatch unit');

      // 2. Optionally update team status to EN_ROUTE
      if (updateTeamStatus) {
        await teamService.updateTeamStatus(selectedTeamId, 'EN_ROUTE', actor);
      }

      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Failed to dispatch response team:', err);
      setError(err.message || 'Dispatch operation failed.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Dispatch Response Unit" maxWidth="md">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="p-3 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono">
            {error}
          </div>
        )}

        <div className="p-3 rounded bg-operations-bg border border-operations-border text-xs font-mono space-y-1">
          <div className="flex justify-between">
            <span className="text-operations-muted">Incident Target:</span>
            <span className="text-white font-bold">{alert.message_id.slice(0, 8)}...</span>
          </div>
          <div className="flex justify-between">
            <span className="text-operations-muted">Category:</span>
            <span className="text-operations-cyan">{alert.category}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-operations-muted">Priority:</span>
            <span className="text-operations-red font-bold">{alert.priority}</span>
          </div>
        </div>

        <div>
          <label className="block text-xs font-mono uppercase text-operations-muted mb-2 font-semibold">
            Select Response Unit
          </label>

          {isLoading ? (
            <div className="py-4 text-center text-xs font-mono text-operations-muted animate-pulse">
              Querying Unit Availability...
            </div>
          ) : teams.length === 0 ? (
            <div className="p-3 rounded bg-operations-card border border-operations-border text-xs text-operations-muted text-center font-mono">
              No active response teams configured in database.
            </div>
          ) : (
            <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
              {teams.map((team) => (
                <div
                  key={team.id}
                  onClick={() => setSelectedTeamId(team.id)}
                  className={`p-3 rounded border cursor-pointer transition-all flex items-center justify-between ${
                    selectedTeamId === team.id
                      ? 'bg-operations-card border-operations-cyan shadow-sm shadow-operations-cyan/20'
                      : 'bg-operations-surface border-operations-border hover:border-operations-border/80'
                  }`}
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded bg-operations-bg border border-operations-border text-operations-cyan">
                      <Truck className="w-4 h-4" />
                    </div>
                    <div>
                      <p className="text-xs font-mono font-bold text-white">{team.name}</p>
                      <p className="text-[10px] font-mono text-operations-muted">{team.team_type} • {team.callsign}</p>
                    </div>
                  </div>
                  <Badge variant={team.status} />
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="pt-2">
          <label className="flex items-center space-x-2 cursor-pointer text-xs font-mono text-operations-text">
            <input
              type="checkbox"
              checked={updateTeamStatus}
              onChange={(e) => setUpdateTeamStatus(e.target.checked)}
              className="rounded bg-operations-bg border-operations-border text-operations-cyan focus:ring-0"
            />
            <span>Automatically mark unit status as <strong className="text-operations-amber">EN ROUTE</strong></span>
          </label>
        </div>

        <div className="flex justify-end space-x-2 pt-2 border-t border-operations-border">
          <Button variant="ghost" type="button" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={isSubmitting}
            disabled={!selectedTeamId || teams.length === 0}
            variant="primary"
          >
            Dispatch Unit
          </Button>
        </div>
      </form>
    </Modal>
  );
};
