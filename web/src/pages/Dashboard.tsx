import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentService } from '../services/incidentService';
import { warningService } from '../services/warningService';
import { teamService } from '../services/teamService';
import { analyticsService, IncidentAnalyticsData } from '../services/analyticsService';
import { EmergencyAlert, PublicWarning, ResponseTeam } from '../types/database';
import { useRealtimeAlerts } from '../hooks/useRealtime';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { formatRelativeTime, formatCoordinates, truncateText } from '../utils/formatters';
import {
  AlertTriangle,
  Radio,
  Users,
  RefreshCw,
  ExternalLink,
  MapPin,
  Clock,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';

export const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<{
    totalReceived: number;
    awaitingVerification: number;
    verifiedActive: number;
    assignedCount: number;
  } | null>(null);
  const [analytics, setAnalytics] = useState<IncidentAnalyticsData | null>(null);
  const [recentAlerts, setRecentAlerts] = useState<EmergencyAlert[]>([]);
  const [activeWarnings, setActiveWarnings] = useState<PublicWarning[]>([]);
  const [teams, setTeams] = useState<ResponseTeam[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setIsLoading(true);
      setError(null);

      const [statsRes, incidentsRes, warningsRes, teamsRes, analyticsRes] = await Promise.all([
        incidentService.getIncidentStats(),
        incidentService.getIncidents({ limit: 8 }),
        warningService.getWarnings('PUBLISHED'),
        teamService.getResponseTeams(),
        analyticsService.getOperationalAnalytics(),
      ]);

      if (statsRes.error) throw new Error(statsRes.error);
      setStats({
        totalReceived: statsRes.totalReceived,
        awaitingVerification: statsRes.awaitingVerification,
        verifiedActive: statsRes.verifiedActive,
        assignedCount: statsRes.assignedCount,
      });

      setRecentAlerts(incidentsRes.data);
      setActiveWarnings(warningsRes.data);
      setTeams(teamsRes.data);
      setAnalytics(analyticsRes.data);
    } catch (err: any) {
      console.error('Error loading dashboard data:', err);
      setError(err.message || 'Failed to query emergency operations data.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Realtime live stream integration
  useRealtimeAlerts((newAlert: EmergencyAlert) => {
    setRecentAlerts((prev) => [newAlert, ...prev.slice(0, 7)]);
    setStats((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        totalReceived: prev.totalReceived + 1,
        awaitingVerification:
          newAlert.verification_status === 'UNVERIFIED'
            ? prev.awaitingVerification + 1
            : prev.awaitingVerification,
      };
    });
  });

  const availableTeamsCount = teams.filter((t) => t.status === 'AVAILABLE').length;
  const deployedTeamsCount = teams.filter((t) => t.status === 'EN_ROUTE' || t.status === 'ON_SCENE').length;

  return (
    <div className="space-y-6">
      {/* Top Bar with Live Refresh */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>OPERATIONAL DASHBOARD</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
              NATIONAL DISASTER GRID
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Decentralized Mesh Gateway Ingestion & Verification Telemetry
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <Button variant="ghost" size="sm" onClick={loadData} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
            Sync Now
          </Button>
          <Link to="/map">
            <Button variant="secondary" size="sm">
              <MapPin className="w-3.5 h-3.5 mr-1.5 text-operations-cyan" />
              Tactical Map
            </Button>
          </Link>
          <Link to="/incidents">
            <Button variant="primary" size="sm">
              <AlertTriangle className="w-3.5 h-3.5 mr-1.5 text-slate-950" />
              Manage Incidents
            </Button>
          </Link>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 flex items-center space-x-3 text-operations-red text-xs font-mono">
          <ShieldAlert className="w-5 h-5 flex-shrink-0" />
          <span>Database Connection Error: {error}</span>
        </div>
      )}

      {/* Primary KPI Metrics Grid with 3D Depth */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 perspective-container">
        <Card className="border-l-4 border-l-operations-cyan card-3d">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase text-operations-muted font-semibold tracking-wider">
              Total Ingested
            </span>
            <Radio className="w-4 h-4 text-operations-cyan animate-pulse" />
          </div>
          <div className="text-2xl font-bold font-mono text-white">
            {isLoading ? '...' : stats?.totalReceived ?? 0}
          </div>
          <div className="text-[10px] font-mono text-operations-muted mt-1">
            Mesh SOS Messages Ingested
          </div>
        </Card>

        <Card className="border-l-4 border-l-operations-amber card-3d card-3d-amber">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase text-operations-muted font-semibold tracking-wider">
              Pending Review
            </span>
            <Clock className="w-4 h-4 text-operations-amber animate-pulse" />
          </div>
          <div className="text-2xl font-bold font-mono text-operations-amber">
            {isLoading ? '...' : stats?.awaitingVerification ?? 0}
          </div>
          <div className="text-[10px] font-mono text-operations-muted mt-1">
            Requires Operator Verification
          </div>
        </Card>

        <Card className="border-l-4 border-l-operations-red card-3d card-3d-red">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase text-operations-muted font-semibold tracking-wider">
              Verified Active
            </span>
            <AlertTriangle className="w-4 h-4 text-operations-red animate-bounce" />
          </div>
          <div className="text-2xl font-bold font-mono text-operations-red">
            {isLoading ? '...' : stats?.verifiedActive ?? 0}
          </div>
          <div className="text-[10px] font-mono text-operations-muted mt-1">
            Confirmed Threat to Life / Property
          </div>
        </Card>

        <Card className="border-l-4 border-l-operations-green card-3d card-3d-green">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase text-operations-muted font-semibold tracking-wider">
              Response Units
            </span>
            <Users className="w-4 h-4 text-operations-green" />
          </div>
          <div className="text-2xl font-bold font-mono text-white">
            {isLoading ? '...' : `${deployedTeamsCount} / ${teams.length}`}
          </div>
          <div className="text-[10px] font-mono text-operations-green mt-1">
            {availableTeamsCount} Ready for Immediate Dispatch
          </div>
        </Card>
      </div>

      {/* Main Content: Ingested Feed & Active Warnings */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Realtime Ingested Incident Feed */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <div className="w-2.5 h-2.5 rounded-full bg-operations-red animate-pulse" />
              <h2 className="font-mono text-sm font-bold uppercase tracking-wider text-white">
                Live Ingested Alerts Feed
              </h2>
            </div>
            <Link
              to="/incidents"
              className="text-xs font-mono text-operations-cyan hover:underline flex items-center space-x-1"
            >
              <span>View All Incidents</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="space-y-3">
            {isLoading ? (
              <div className="p-8 text-center text-xs font-mono text-operations-muted animate-pulse border border-operations-border rounded bg-operations-surface">
                Loading Ingestion Telemetry...
              </div>
            ) : recentAlerts.length === 0 ? (
              <div className="p-8 text-center text-xs font-mono text-operations-muted border border-operations-border rounded bg-operations-surface">
                No emergency incidents currently recorded in the central database.
              </div>
            ) : (
              recentAlerts.map((alert) => (
                <Link
                  key={alert.message_id}
                  to={`/incidents/${alert.message_id}`}
                  className="block p-4 rounded bg-operations-surface border border-operations-border hover:border-operations-cyan/50 hover:bg-operations-card/80 transition-all group"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1">
                      <div className="flex items-center space-x-2">
                        <Badge variant={alert.priority} />
                        <span className="font-mono text-xs font-bold text-white group-hover:text-operations-cyan transition-colors">
                          {alert.category}
                        </span>
                        <span className="text-[10px] font-mono text-operations-muted">
                          • ID: {alert.message_id.slice(0, 8)}
                        </span>
                      </div>
                      <p className="text-xs text-operations-text font-mono">
                        {truncateText(alert.description || 'No description provided.', 120)}
                      </p>
                      <div className="flex flex-wrap items-center gap-3 pt-1 text-[10px] font-mono text-operations-muted">
                        <span className="flex items-center space-x-1">
                          <MapPin className="w-3 h-3 text-operations-cyan" />
                          <span>{formatCoordinates(alert.latitude, alert.longitude, alert.accuracy_meters)}</span>
                        </span>
                        <span>• Hops: <strong className="text-white">{alert.hop_count}</strong></span>
                        <span>• Gateway: <span className="text-operations-muted">{alert.gateway_device_id.slice(0, 6)}</span></span>
                      </div>
                    </div>

                    <div className="flex flex-col items-end space-y-2 flex-shrink-0">
                      <Badge variant={alert.verification_status} />
                      <span className="text-[10px] font-mono text-operations-muted">
                        {formatRelativeTime(alert.created_at)}
                      </span>
                    </div>
                  </div>
                </Link>
              ))
            )}
          </div>
        </div>

        {/* Right Column: Active Warnings & Response Summary */}
        <div className="space-y-6">
          {/* Active Public Warnings Card */}
          <Card
            title="Public Emergency Broadcasts"
            headerAction={
              <Link to="/warnings" className="text-xs font-mono text-operations-cyan hover:underline flex items-center">
                <span>Manage</span>
                <ExternalLink className="w-3 h-3 ml-1" />
              </Link>
            }
          >
            {activeWarnings.length === 0 ? (
              <div className="py-6 text-center text-xs font-mono text-operations-muted">
                No active public warnings currently published.
              </div>
            ) : (
              <div className="space-y-3">
                {activeWarnings.map((warning) => (
                  <div
                    key={warning.id}
                    className="p-3 rounded bg-operations-bg border border-operations-border space-y-1.5"
                  >
                    <div className="flex items-center justify-between">
                      <Badge variant={warning.severity} />
                      <span className="text-[10px] font-mono text-operations-muted">
                        {formatRelativeTime(warning.created_at)}
                      </span>
                    </div>
                    <p className="text-xs font-bold font-mono text-white">{warning.title}</p>
                    <p className="text-[11px] text-operations-muted line-clamp-2">
                      {warning.description}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Tactical Mesh Network 3D Radar HUD */}
          <Card title="Autonomous Mesh 3D Radar" is3D={true} className="border-t-2 border-t-operations-cyan">
            <div className="flex flex-col items-center justify-center p-3 relative select-none">
              <div className="relative w-44 h-44 rounded-full border-2 border-operations-cyan/30 bg-operations-bg/90 flex items-center justify-center shadow-inner shadow-cyan-950/50 overflow-hidden">
                {/* Concentric distance rings */}
                <div className="absolute w-32 h-32 rounded-full border border-operations-cyan/20"></div>
                <div className="absolute w-20 h-20 rounded-full border border-operations-cyan/20"></div>
                <div className="absolute w-8 h-8 rounded-full border border-operations-cyan/30"></div>
                {/* Crosshairs */}
                <div className="absolute w-full h-[1px] bg-operations-cyan/20"></div>
                <div className="absolute h-full w-[1px] bg-operations-cyan/20"></div>

                {/* Rotating 3D Radar Sweep */}
                <div
                  className="absolute inset-0 rounded-full animate-radar-sweep pointer-events-none"
                  style={{
                    background: 'conic-gradient(from 0deg, rgba(6, 182, 212, 0.45) 0deg, rgba(6, 182, 212, 0) 65deg, transparent 65deg)',
                  }}
                ></div>

                {/* Simulated active mesh nodes & SOS beacon pings */}
                <div className="absolute top-10 left-12 w-2.5 h-2.5 rounded-full bg-operations-red animate-ping" title="Active SOS Beacon"></div>
                <div className="absolute top-10 left-12 w-2 h-2 rounded-full bg-operations-red"></div>

                <div className="absolute bottom-12 right-14 w-2 h-2 rounded-full bg-operations-cyan animate-pulse" title="Mesh Relay Gateway"></div>
                <div className="absolute top-16 right-10 w-1.5 h-1.5 rounded-full bg-operations-green" title="Deployed Response Unit"></div>

                {/* Center Station */}
                <div className="w-3 h-3 rounded-full bg-operations-cyan shadow-md shadow-cyan-400 z-10"></div>
              </div>

              <div className="mt-3 flex items-center justify-between w-full text-[10px] font-mono text-operations-muted">
                <span className="flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-operations-cyan animate-ping"></span>
                  RANGE: 100 KM
                </span>
                <span className="text-operations-cyan font-bold tracking-wider">P2P CLUSTER ACTIVE</span>
              </div>
            </div>
          </Card>

          {/* Incident Category Distribution */}
          <Card title="Threat Classification Breakdown">
            {!analytics || Object.keys(analytics.categoryDistribution).length === 0 ? (
              <div className="py-6 text-center text-xs font-mono text-operations-muted">
                No categorized incidents recorded.
              </div>
            ) : (
              <div className="space-y-2.5">
                {Object.entries(analytics.categoryDistribution).map(([category, count]) => {
                  const percentage =
                    analytics.totalAlerts > 0
                      ? Math.round((count / analytics.totalAlerts) * 100)
                      : 0;
                  return (
                    <div key={category} className="space-y-1 font-mono text-xs">
                      <div className="flex justify-between text-operations-text">
                        <span>{category}</span>
                        <span className="text-operations-muted">
                          {count} ({percentage}%)
                        </span>
                      </div>
                      <div className="w-full h-1.5 bg-operations-bg rounded-full overflow-hidden">
                        <div
                          className="h-full bg-operations-cyan"
                          style={{ width: `${percentage}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};
