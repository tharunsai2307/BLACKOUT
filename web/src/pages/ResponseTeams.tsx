import React, { useState, useEffect } from 'react';
import { teamService } from '../services/teamService';
import { ResponseTeam, TeamStatus, TeamType } from '../types/database';
import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import {
  Plus,
  RefreshCw,
  Phone,
  MapPin,
} from 'lucide-react';

export const ResponseTeams: React.FC = () => {
  const { user, profile, canDispatch, isSuperAdmin } = useAuth();
  const [teams, setTeams] = useState<ResponseTeam[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // New Team Modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newTeamName, setNewTeamName] = useState('');
  const [newTeamType, setNewTeamType] = useState<TeamType>('SEARCH_AND_RESCUE');
  const [newCallSign, setNewCallSign] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newBaseStation, setNewBaseStation] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadTeams = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await teamService.getResponseTeams();
      if (res.error) throw new Error(res.error);
      setTeams(res.data);
    } catch (err: any) {
      console.error('Failed to load response teams:', err);
      setError(err.message || 'Error querying response units.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadTeams();
  }, []);

  const handleStatusChange = async (teamId: string, newStatus: TeamStatus) => {
    try {
      const actor = {
        id: user?.id || profile?.id || 'op-operator',
        email: user?.email || profile?.email || 'operator@blackout.ops',
        role: profile?.role || 'CONTROL_ROOM_OPERATOR',
      };
      const res = await teamService.updateTeamStatus(teamId, newStatus, actor);
      if (res.error) throw new Error(res.error);
      loadTeams();
    } catch (err: any) {
      alert(`Status update failed: ${err.message}`);
    }
  };

  const handleCreateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTeamName.trim() || !newCallSign.trim()) {
      alert('Please provide both Team Name and Tactical Callsign.');
      return;
    }

    try {
      setIsSubmitting(true);
      const actor = {
        id: user?.id || profile?.id || 'op-admin',
        email: user?.email || profile?.email || 'admin@blackout.ops',
        role: profile?.role || 'CONTROL_ROOM_OPERATOR',
      };

      const res = await teamService.createResponseTeam({
        name: newTeamName.trim(),
        team_type: newTeamType,
        callsign: newCallSign.trim().toUpperCase(),
        contact_phone: newPhone.trim() || undefined,
        base_location: newBaseStation.trim() || undefined,
        capacity: 4,
        actor,
      });

      if (res.error) throw new Error(res.error);

      setIsCreateOpen(false);
      setNewTeamName('');
      setNewCallSign('');
      setNewPhone('');
      setNewBaseStation('');
      await loadTeams();
    } catch (err: any) {
      alert(`Team creation failed: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>FIELD RESPONSE UNITS</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
              {teams.length} UNITS CONFIGURED
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Emergency rescue units, medical teams, evacuation squads, and operational status
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <Button variant="ghost" size="sm" onClick={loadTeams} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>

          {(canDispatch() || isSuperAdmin()) && (
            <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
              <Plus className="w-3.5 h-3.5 mr-1.5" />
              Register Unit
            </Button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono">
          {error}
        </div>
      )}

      {/* Response Teams Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {isLoading ? (
          <div className="col-span-full py-12 text-center text-xs font-mono text-operations-muted animate-pulse">
            Querying Field Response Units...
          </div>
        ) : teams.length === 0 ? (
          <div className="col-span-full py-12 text-center text-xs font-mono text-operations-muted">
            No response teams configured in the database.
          </div>
        ) : (
          teams.map((team) => (
            <Card key={team.id} className="space-y-4">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-mono text-sm font-bold text-white flex items-center space-x-2">
                    <span>{team.name}</span>
                  </h3>
                  <p className="text-[10px] font-mono text-operations-cyan mt-0.5">
                    {team.team_type} • CALL SIGN: <strong>{team.callsign}</strong>
                  </p>
                </div>
                <Badge variant={team.status} />
              </div>

              {/* Specs / Comms Info */}
              <div className="space-y-1.5 text-[11px] font-mono text-operations-muted bg-operations-bg p-3 rounded border border-operations-border">
                {team.contact_phone && (
                  <div className="flex items-center space-x-2">
                    <Phone className="w-3.5 h-3.5 text-operations-green" />
                    <span>Phone: {team.contact_phone}</span>
                  </div>
                )}
                {team.base_location && (
                  <div className="flex items-center space-x-2">
                    <MapPin className="w-3.5 h-3.5 text-operations-amber" />
                    <span>Base: {team.base_location}</span>
                  </div>
                )}
              </div>

              {/* Status Update Trigger */}
              {canDispatch() && (
                <div className="pt-2 border-t border-operations-border flex items-center justify-between">
                  <span className="text-[10px] font-mono text-operations-muted uppercase">
                    Update Status:
                  </span>
                  <select
                    value={team.status}
                    onChange={(e) => handleStatusChange(team.id, e.target.value as TeamStatus)}
                    className="bg-operations-bg border border-operations-border rounded px-2 py-1 text-[11px] font-mono text-white focus:outline-none focus:border-operations-cyan"
                  >
                    <option value="AVAILABLE">AVAILABLE</option>
                    <option value="ASSIGNED">ASSIGNED</option>
                    <option value="EN_ROUTE">EN ROUTE</option>
                    <option value="ON_SCENE">ON SCENE</option>
                    <option value="COMPLETED">COMPLETED</option>
                    <option value="UNAVAILABLE">UNAVAILABLE</option>
                  </select>
                </div>
              )}
            </Card>
          ))
        )}
      </div>

      {/* Create Team Modal */}
      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Register New Response Unit"
        maxWidth="md"
      >
        <form onSubmit={handleCreateTeam} className="space-y-4">
          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Team / Unit Name *
            </label>
            <input
              type="text"
              required
              value={newTeamName}
              onChange={(e) => setNewTeamName(e.target.value)}
              placeholder="e.g. Foxtrot Flood Evac Unit"
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Unit Type *
              </label>
              <select
                value={newTeamType}
                onChange={(e) => setNewTeamType(e.target.value as TeamType)}
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              >
                <option value="SEARCH_AND_RESCUE">Search & Rescue</option>
                <option value="MEDICAL_RESPONSE">Medical Response</option>
                <option value="EVACUATION_CREW">Evacuation Crew</option>
                <option value="FIREFIGHTING">Firefighting</option>
                <option value="HAZMAT">Hazmat</option>
                <option value="DISASTER_LOGISTICS">Disaster Logistics</option>
                <option value="GENERAL">General</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Call Sign *
              </label>
              <input
                type="text"
                required
                value={newCallSign}
                onChange={(e) => setNewCallSign(e.target.value)}
                placeholder="e.g. FOXTROT-1"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan uppercase"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Contact Phone
              </label>
              <input
                type="text"
                value={newPhone}
                onChange={(e) => setNewPhone(e.target.value)}
                placeholder="e.g. +91 9876543210"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>

            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Base Location
              </label>
              <input
                type="text"
                value={newBaseStation}
                onChange={(e) => setNewBaseStation(e.target.value)}
                placeholder="e.g. Central Emergency Depot 4"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>
          </div>

          <div className="flex justify-end space-x-2 pt-2 border-t border-operations-border">
            <Button variant="ghost" type="button" onClick={() => setIsCreateOpen(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting} variant="primary">
              Register Unit
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
