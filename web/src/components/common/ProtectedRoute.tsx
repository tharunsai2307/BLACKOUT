import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { AppRole } from '../../types/database';
import { ShieldAlert, RefreshCw } from 'lucide-react';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: AppRole[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { user, profile, loading, hasRole } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen bg-operations-bg flex flex-col items-center justify-center p-4">
        <div className="flex items-center space-x-3 text-operations-cyan animate-pulse">
          <RefreshCw className="w-8 h-8 animate-spin" />
          <span className="font-mono text-sm tracking-wider uppercase font-semibold">
            Authenticating Emergency Operations Console...
          </span>
        </div>
      </div>
    );
  }

  if (!user || !profile) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && allowedRoles.length > 0 && !hasRole(allowedRoles)) {
    return (
      <div className="min-h-[80vh] flex flex-col items-center justify-center p-6 text-center">
        <div className="w-16 h-16 rounded-full bg-operations-red/10 border border-operations-red/30 flex items-center justify-center text-operations-red mb-4">
          <ShieldAlert className="w-8 h-8" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2 font-mono uppercase tracking-wide">
          Access Restricted by Role-Based Policy
        </h2>
        <p className="text-operations-muted max-w-md text-sm mb-6">
          Your active role (<span className="text-operations-cyan font-mono">{profile.role}</span>) does not have authorization to view or manage this command station resource.
        </p>
        <button
          onClick={() => window.history.back()}
          className="px-4 py-2 bg-operations-surface border border-operations-border text-sm rounded hover:border-operations-cyan text-operations-text transition-colors font-mono"
        >
          Return to Authorized Console
        </button>
      </div>
    );
  }

  return <>{children}</>;
};
