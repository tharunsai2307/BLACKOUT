import { useEffect, useState } from 'react';
import { supabase } from '../services/supabase';
import { EmergencyAlert } from '../types/database';

export type RealtimeStatus = 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED';

interface UseRealtimeOptions {
  table?: 'emergency_alerts' | 'public_warnings' | 'response_teams';
  onEvent?: (payload: any) => void;
  enabled?: boolean;
}

export function useRealtime({ table = 'emergency_alerts', onEvent, enabled = true }: UseRealtimeOptions = {}) {
  const [status, setStatus] = useState<RealtimeStatus>('CONNECTING');
  const [lastEventTime, setLastEventTime] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      setStatus('DISCONNECTED');
      return;
    }

    setStatus('CONNECTING');
    const channelName = `realtime_${table}_${Math.floor(Math.random() * 10000)}`;

    const channel = supabase
      .channel(channelName)
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table },
        (payload) => {
          setLastEventTime(new Date().toISOString());
          if (onEvent) {
            onEvent(payload);
          }
        }
      )
      .subscribe((subscribeStatus) => {
        if (subscribeStatus === 'SUBSCRIBED') {
          setStatus('CONNECTED');
        } else if (subscribeStatus === 'CLOSED' || subscribeStatus === 'CHANNEL_ERROR') {
          setStatus('DISCONNECTED');
        } else {
          setStatus('CONNECTING');
        }
      });

    return () => {
      supabase.removeChannel(channel);
    };
  }, [table, enabled]);

  return { status, lastEventTime };
}

export function useRealtimeStatus(): RealtimeStatus {
  const { status } = useRealtime();
  return status;
}

export function useRealtimeAlerts(onNewAlert?: (alert: EmergencyAlert) => void) {
  return useRealtime({
    table: 'emergency_alerts',
    onEvent: (payload) => {
      if (payload.eventType === 'INSERT' && onNewAlert) {
        onNewAlert(payload.new as EmergencyAlert);
      }
    },
  });
}
