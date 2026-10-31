import React, { useState, useEffect } from 'react';
import { analyticsService, IncidentAnalyticsData } from '../services/analyticsService';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  CartesianGrid,
} from 'recharts';
import { RefreshCw } from 'lucide-react';

export const Analytics: React.FC = () => {
  const [data, setData] = useState<IncidentAnalyticsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAnalytics = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await analyticsService.getOperationalAnalytics();
      if (res.error) throw new Error(res.error);
      setData(res.data);
    } catch (err: any) {
      console.error('Failed to load analytics:', err);
      setError(err.message || 'Error compiling analytics from database.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, []);

  const categoryChartData = data
    ? Object.entries(data.categoryDistribution).map(([name, count]) => ({ name, count }))
    : [];

  const priorityChartData = data
    ? Object.entries(data.priorityDistribution).map(([name, count]) => ({ name, count }))
    : [];

  const hopChartData = data
    ? Object.entries(data.hopCountDistribution).map(([hops, count]) => ({ hops: `${hops} hops`, count }))
    : [];

  const totalHops = data
    ? Object.entries(data.hopCountDistribution).reduce((acc, [h, count]) => acc + Number(h) * count, 0)
    : 0;
  const meanHops = data && data.totalAlerts > 0 ? (totalHops / data.totalAlerts).toFixed(1) : '0.0';

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>DISASTER ANALYTICS & TELEMETRY</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
              AGGREGATED METRICS
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Real mathematical distributions derived directly from incoming mesh gateways
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <Button variant="ghost" size="sm" onClick={loadAnalytics} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono">
          {error}
        </div>
      )}

      {/* Primary KPI Row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card className="border-l-4 border-l-operations-cyan">
          <span className="text-[10px] font-mono uppercase text-operations-muted font-semibold">
            Total Ingested Alerts
          </span>
          <p className="text-2xl font-bold font-mono text-white mt-1">
            {isLoading ? '...' : data?.totalAlerts ?? 0}
          </p>
          <span className="text-[10px] font-mono text-operations-muted mt-1 block">
            Mesh Emergency Records
          </span>
        </Card>

        <Card className="border-l-4 border-l-operations-green">
          <span className="text-[10px] font-mono uppercase text-operations-muted font-semibold">
            GPS Coverage Ratio
          </span>
          <p className="text-2xl font-bold font-mono text-operations-green mt-1">
            {isLoading ? '...' : `${data?.locationFixStats.percentageWithGps ?? 0}%`}
          </p>
          <span className="text-[10px] font-mono text-operations-muted mt-1 block">
            {data?.locationFixStats.withGps ?? 0} with Valid Spatial Fix
          </span>
        </Card>

        <Card className="border-l-4 border-l-operations-amber">
          <span className="text-[10px] font-mono uppercase text-operations-muted font-semibold">
            Mean Relay Hops
          </span>
          <p className="text-2xl font-bold font-mono text-operations-amber mt-1">
            {isLoading ? '...' : `${meanHops} hops`}
          </p>
          <span className="text-[10px] font-mono text-operations-muted mt-1 block">
            Store-Carry-Forward Traversal
          </span>
        </Card>

        <Card className="border-l-4 border-l-operations-blue">
          <span className="text-[10px] font-mono uppercase text-operations-muted font-semibold">
            Reporting Gateways
          </span>
          <p className="text-2xl font-bold font-mono text-white mt-1">
            {isLoading ? '...' : Object.keys(data?.gatewayDistribution || {}).length}
          </p>
          <span className="text-[10px] font-mono text-operations-muted mt-1 block">
            Active Uplink Android Devices
          </span>
        </Card>
      </div>

      {/* Visual Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Breakdown Bar Chart */}
        <Card title="Threat Classification Breakdown">
          {isLoading ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              Compiling...
            </div>
          ) : categoryChartData.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              No category data recorded.
            </div>
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={categoryChartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                  <XAxis
                    dataKey="name"
                    stroke="#64748B"
                    fontSize={10}
                    tickLine={false}
                    interval={0}
                    angle={-20}
                    textAnchor="end"
                  />
                  <YAxis stroke="#64748B" fontSize={10} tickLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0F172A', borderColor: '#1E293B', color: '#F8FAFC', fontSize: '11px', fontFamily: 'monospace' }}
                  />
                  <Bar dataKey="count" fill="#06B6D4" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        {/* Priority Distribution Pie Chart */}
        <Card title="Incident Priority Distribution">
          {isLoading ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              Compiling...
            </div>
          ) : priorityChartData.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              No priority data recorded.
            </div>
          ) : (
            <div className="h-64 w-full flex items-center justify-center">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={priorityChartData}
                    dataKey="count"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={80}
                    innerRadius={50}
                    paddingAngle={3}
                  >
                    {priorityChartData.map((entry, index) => (
                      <Cell
                        key={`cell-${index}`}
                        fill={
                          entry.name === 'CRITICAL'
                            ? '#EF4444'
                            : entry.name === 'HIGH'
                            ? '#F59E0B'
                            : entry.name === 'MODERATE'
                            ? '#06B6D4'
                            : '#64748B'
                        }
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0F172A', borderColor: '#1E293B', color: '#F8FAFC', fontSize: '11px', fontFamily: 'monospace' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        {/* Daily Ingestion Trend */}
        <Card title="7-Day Incident Ingestion Trend">
          {isLoading ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              Compiling...
            </div>
          ) : !data || data.dailyTrend.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              No daily historical trend recorded.
            </div>
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.dailyTrend} margin={{ top: 10, right: 10, left: -20, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
                  <XAxis dataKey="date" stroke="#64748B" fontSize={10} tickLine={false} />
                  <YAxis stroke="#64748B" fontSize={10} tickLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0F172A', borderColor: '#1E293B', color: '#F8FAFC', fontSize: '11px', fontFamily: 'monospace' }}
                  />
                  <Line type="monotone" dataKey="count" stroke="#10B981" strokeWidth={2} dot={{ r: 4, fill: '#10B981' }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        {/* Mesh Hop Traversal Distribution */}
        <Card title="Store-Carry-Forward Mesh Hop Distribution">
          {isLoading ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              Compiling...
            </div>
          ) : hopChartData.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-xs font-mono text-operations-muted">
              No hop data recorded.
            </div>
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={hopChartData} margin={{ top: 10, right: 10, left: -20, bottom: 5 }}>
                  <XAxis dataKey="hops" stroke="#64748B" fontSize={10} tickLine={false} />
                  <YAxis stroke="#64748B" fontSize={10} tickLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0F172A', borderColor: '#1E293B', color: '#F8FAFC', fontSize: '11px', fontFamily: 'monospace' }}
                  />
                  <Bar dataKey="count" fill="#F59E0B" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};
