import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  AlertTriangle,
  MapPin,
  Users,
  Megaphone,
  BarChart3,
  FileText,
  Settings,
  ShieldCheck,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

const navItems = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Incidents Feed', path: '/incidents', icon: AlertTriangle },
  { name: 'Live Map', path: '/map', icon: MapPin },
  { name: 'Response Teams', path: '/teams', icon: Users },
  { name: 'Public Warnings', path: '/warnings', icon: Megaphone },
  { name: 'Analytics & Trends', path: '/analytics', icon: BarChart3 },
  { name: 'Audit Logs', path: '/audit', icon: FileText, requiredRoles: ['SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR'] },
  { name: 'Console Settings', path: '/settings', icon: Settings },
];

export const Sidebar: React.FC = () => {
  const { hasRole, profile } = useAuth();

  return (
    <aside className="w-64 bg-operations-surface border-r border-operations-border flex flex-col justify-between select-none">
      <div className="py-4">
        {/* Operations Section Header */}
        <div className="px-6 pb-3 mb-2 border-b border-operations-border/40">
          <p className="text-[10px] font-mono uppercase text-operations-muted font-bold tracking-widest">
            Tactical Navigation
          </p>
        </div>

        {/* Navigation Links */}
        <nav className="space-y-1 px-3">
          {navItems.map((item) => {
            if (item.requiredRoles && !hasRole(item.requiredRoles as any)) {
              return null;
            }

            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center space-x-3 px-3 py-2.5 rounded text-xs font-mono tracking-wide transition-all ${
                    isActive
                      ? 'bg-operations-card text-operations-cyan border border-operations-cyan/30 shadow-sm font-semibold'
                      : 'text-operations-muted hover:text-white hover:bg-operations-card/50 border border-transparent'
                  }`
                }
              >
                <Icon className="w-4 h-4 flex-shrink-0" />
                <span>{item.name}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Security Status Footer */}
      <div className="p-4 m-3 rounded bg-operations-bg border border-operations-border">
        <div className="flex items-center space-x-2 text-operations-green text-[11px] font-mono font-medium mb-1">
          <ShieldCheck className="w-4 h-4" />
          <span>ZERO-TRUST RLS ACTIVE</span>
        </div>
        <p className="text-[10px] text-operations-muted font-mono leading-tight">
          Role: <span className="text-operations-cyan">{profile?.role || 'AUTHENTICATED'}</span>
        </p>
        <p className="text-[9px] text-operations-muted/70 font-mono mt-1">
          Government Incident Response Network
        </p>
      </div>
    </aside>
  );
};
