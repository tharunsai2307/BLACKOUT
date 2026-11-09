import React from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useRealtimeStatus } from '../../hooks/useRealtime';
import { isPlaceholderUrl } from '../../services/offlineStore';
import { LogOut, Shield, Wifi, WifiOff, HardDrive } from 'lucide-react';

export const Navbar: React.FC = () => {
  const { profile, logout } = useAuth();
  const realtimeStatus = useRealtimeStatus();
  const isOffline = isPlaceholderUrl(import.meta.env.VITE_SUPABASE_URL);

  return (
    <header className="h-16 bg-operations-surface border-b border-operations-border px-4 lg:px-6 flex items-center justify-between select-none z-30 sticky top-0">
      {/* Brand & Operation Identifier */}
      <div className="flex items-center space-x-3">
        <div className="relative group perspective-container">
          <img
            src="/logo.png"
            alt="BLACKOUT"
            className="w-10 h-10 rounded-lg object-cover border border-operations-cyan/50 shadow-md shadow-operations-cyan/20 animate-beacon-3d group-hover:scale-105 transition-transform duration-300"
          />
          <div className="absolute -inset-0.5 rounded-lg bg-gradient-to-r from-operations-red/30 to-operations-cyan/30 blur opacity-40 group-hover:opacity-75 transition-opacity duration-300 -z-10"></div>
        </div>
        <div>
          <div className="flex items-center space-x-2">
            <span className="font-mono font-black tracking-widest text-white text-base">BLACKOUT</span>
            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-operations-border text-operations-cyan uppercase font-bold tracking-wider">
              OPS CENTER v6.0
            </span>
          </div>
          <div className="text-[11px] text-operations-muted font-mono tracking-tight flex items-center space-x-1.5">
            <span>GOVERNMENT DISASTER RESPONSE & RESCUE COMMAND</span>
          </div>
        </div>
      </div>

      {/* Realtime Stream & User Session */}
      <div className="flex items-center space-x-4">
        {/* Realtime / Offline Indicator */}
        <div className="hidden sm:flex items-center px-3 py-1 rounded bg-operations-bg border border-operations-border text-xs font-mono">
          {isOffline ? (
            <div className="flex items-center space-x-2 text-operations-amber" title="Supabase URL not configured in web/.env. Operating in local sandbox store.">
              <HardDrive className="w-3.5 h-3.5 text-operations-amber animate-pulse" />
              <span className="font-semibold tracking-wide">LOCAL OFFLINE SANDBOX</span>
            </div>
          ) : realtimeStatus === 'CONNECTED' ? (
            <div className="flex items-center space-x-2 text-operations-green">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-operations-green opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-operations-green"></span>
              </span>
              <Wifi className="w-3.5 h-3.5" />
              <span className="font-semibold tracking-wide">LIVE REALTIME STREAM</span>
            </div>
          ) : realtimeStatus === 'CONNECTING' ? (
            <div className="flex items-center space-x-2 text-operations-amber">
              <span className="inline-block h-2 w-2 rounded-full bg-operations-amber animate-pulse"></span>
              <Wifi className="w-3.5 h-3.5" />
              <span>SYNCHRONIZING FEED...</span>
            </div>
          ) : (
            <div className="flex items-center space-x-2 text-operations-red">
              <span className="inline-block h-2 w-2 rounded-full bg-operations-red"></span>
              <WifiOff className="w-3.5 h-3.5" />
              <span>OFFLINE (MANUAL REFRESH)</span>
            </div>
          )}
        </div>

        {/* User Role & Organization Badge */}
        {profile && (
          <div className="flex items-center space-x-3 pl-2 border-l border-operations-border">
            <div className="hidden md:flex flex-col text-right">
              <span className="text-xs font-medium text-white truncate max-w-[140px]">
                {profile.full_name}
              </span>
              <div className="flex items-center justify-end space-x-1 text-[10px] font-mono text-operations-muted">
                <Shield className="w-3.5 h-3.5 text-operations-cyan" />
                <span className="text-operations-cyan font-bold">{profile.role.replace(/_/g, ' ')}</span>
                {profile.organization && <span>• {profile.organization}</span>}
              </div>
            </div>

            <button
              onClick={() => logout()}
              title="Sign Out of Operations Console"
              className="p-2 text-operations-muted hover:text-operations-red hover:bg-operations-card rounded border border-transparent hover:border-operations-border transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
