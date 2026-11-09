import { supabase } from './supabase';
import { offlineStore, isPlaceholderUrl } from './offlineStore';

export interface IncidentAnalyticsData {
  totalAlerts: number;
  categoryDistribution: Record<string, number>;
  priorityDistribution: Record<string, number>;
  statusDistribution: Record<string, number>;
  verificationDistribution: Record<string, number>;
  hopCountDistribution: Record<number, number>;
  gatewayDistribution: Record<string, number>;
  locationFixStats: { withGps: number; withoutGps: number; percentageWithGps: number };
  dailyTrend: { date: string; count: number }[];
}

export const analyticsService = {
  async getOperationalAnalytics(dateFrom?: string, dateTo?: string): Promise<{
    data: IncidentAnalyticsData | null;
    error: string | null;
  }> {
    const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;

    if (isPlaceholderUrl(supabaseUrl)) {
      return { data: this.computeOfflineAnalytics(), error: null };
    }

    try {
      // 1. Attempt high-performance SQL aggregation RPC (0 client-side memory overhead)
      const { data: rpcData, error: rpcError } = await supabase.rpc('get_operational_analytics_summary', {
        p_date_from: dateFrom || null,
        p_date_to: dateTo || null,
      });

      if (!rpcError && rpcData && typeof rpcData === 'object' && 'totalAlerts' in rpcData) {
        return {
          data: {
            totalAlerts: Number(rpcData.totalAlerts) || 0,
            categoryDistribution: rpcData.categoryDistribution || {},
            priorityDistribution: rpcData.priorityDistribution || {},
            statusDistribution: rpcData.statusDistribution || {},
            verificationDistribution: rpcData.verificationDistribution || {},
            hopCountDistribution: rpcData.hopCountDistribution || {},
            gatewayDistribution: rpcData.gatewayDistribution || {},
            locationFixStats: rpcData.locationFixStats || { withGps: 0, withoutGps: 0, percentageWithGps: 0 },
            dailyTrend: rpcData.dailyTrend || [],
          },
          error: null,
        };
      }

      // 2. Fallback to direct query if RPC is pending migration
      let query = supabase
        .from('emergency_alerts')
        .select('id, category, priority, operator_status, verification_status, hop_count, gateway_device_id, latitude, longitude, created_at')
        .order('created_at', { ascending: true });

      if (dateFrom) {
        query = query.gte('created_at', dateFrom);
      }
      if (dateTo) {
        query = query.lte('created_at', dateTo);
      }

      const { data: alerts, error } = await query;
      if (error) throw error;

      return { data: this.aggregateAlerts(alerts || []), error: null };
    } catch (err: any) {
      console.warn('analyticsService network error, falling back to offline analytics:', err.message);
      return { data: this.computeOfflineAnalytics(), error: null };
    }
  },

  computeOfflineAnalytics(): IncidentAnalyticsData {
    const alerts = offlineStore.getAlerts();
    return this.aggregateAlerts(alerts);
  },

  aggregateAlerts(alerts: any[]): IncidentAnalyticsData {
    const totalAlerts = alerts.length;
    const categoryDistribution: Record<string, number> = {};
    const priorityDistribution: Record<string, number> = {};
    const statusDistribution: Record<string, number> = {};
    const verificationDistribution: Record<string, number> = {};
    const hopCountDistribution: Record<number, number> = {};
    const gatewayDistribution: Record<string, number> = {};
    const dailyMap: Record<string, number> = {};
    let withGps = 0;

    for (const a of alerts) {
      categoryDistribution[a.category] = (categoryDistribution[a.category] || 0) + 1;
      priorityDistribution[a.priority] = (priorityDistribution[a.priority] || 0) + 1;
      statusDistribution[a.operator_status] = (statusDistribution[a.operator_status] || 0) + 1;
      verificationDistribution[a.verification_status] = (verificationDistribution[a.verification_status] || 0) + 1;

      const hops = a.hop_count || 0;
      hopCountDistribution[hops] = (hopCountDistribution[hops] || 0) + 1;

      const gw = a.gateway_device_id || 'NODE-GW-RELAY-1';
      gatewayDistribution[gw] = (gatewayDistribution[gw] || 0) + 1;

      if (a.latitude !== null && a.longitude !== null && !(a.latitude === 0 && a.longitude === 0)) {
        withGps++;
      }

      try {
        const dateStr = a.created_at.split('T')[0];
        dailyMap[dateStr] = (dailyMap[dateStr] || 0) + 1;
      } catch (ignored) {}
    }

    const dailyTrend = Object.keys(dailyMap)
      .sort()
      .map((date) => ({ date, count: dailyMap[date] }));

    const withoutGps = totalAlerts - withGps;
    const percentageWithGps = totalAlerts > 0 ? Math.round((withGps / totalAlerts) * 100) : 0;

    return {
      totalAlerts,
      categoryDistribution,
      priorityDistribution,
      statusDistribution,
      verificationDistribution,
      hopCountDistribution,
      gatewayDistribution,
      locationFixStats: { withGps, withoutGps, percentageWithGps },
      dailyTrend,
    };
  },
};
