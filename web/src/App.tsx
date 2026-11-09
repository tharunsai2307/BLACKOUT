import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';
import { Loader2 } from 'lucide-react';

// Route-Based Code Splitting via React.lazy
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const Dashboard = lazy(() => import('./pages/Dashboard').then((m) => ({ default: m.Dashboard })));
const Incidents = lazy(() => import('./pages/Incidents').then((m) => ({ default: m.Incidents })));
const IncidentDetails = lazy(() => import('./pages/IncidentDetails').then((m) => ({ default: m.IncidentDetails })));
const LiveMap = lazy(() => import('./pages/LiveMap').then((m) => ({ default: m.LiveMap })));
const ResponseTeams = lazy(() => import('./pages/ResponseTeams').then((m) => ({ default: m.ResponseTeams })));
const PublicWarnings = lazy(() => import('./pages/PublicWarnings').then((m) => ({ default: m.PublicWarnings })));
const Analytics = lazy(() => import('./pages/Analytics').then((m) => ({ default: m.Analytics })));
const AuditLogs = lazy(() => import('./pages/AuditLogs').then((m) => ({ default: m.AuditLogs })));
const Settings = lazy(() => import('./pages/Settings').then((m) => ({ default: m.Settings })));

// Sleek tactical fallback component for route transitions
const PageSuspenseFallback: React.FC = () => (
  <div className="flex-1 flex flex-col items-center justify-center p-12 min-h-[300px] text-center">
    <div className="w-12 h-12 rounded-full border-2 border-operations-border bg-operations-surface flex items-center justify-center mb-3 shadow-lg shadow-black/40">
      <Loader2 className="w-6 h-6 text-operations-cyan animate-spin" />
    </div>
    <span className="text-xs font-mono font-bold uppercase tracking-widest text-slate-300">
      Loading Command Module...
    </span>
    <span className="text-[10px] font-mono text-operations-muted mt-1">
      INITIALIZING OPERATIONS CONSOLE
    </span>
  </div>
);

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<PageSuspenseFallback />}>
          <Routes>
            {/* Public Login Route */}
            <Route path="/login" element={<Login />} />

            {/* Protected Command Center Console Routes */}
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="incidents" element={<Incidents />} />
              <Route path="incidents/:id" element={<IncidentDetails />} />
              <Route path="map" element={<LiveMap />} />
              <Route path="teams" element={<ResponseTeams />} />
              <Route path="warnings" element={<PublicWarnings />} />
              <Route path="analytics" element={<Analytics />} />
              <Route
                path="audit"
                element={
                  <ProtectedRoute allowedRoles={['SUPER_ADMIN', 'CONTROL_ROOM_OPERATOR']}>
                    <AuditLogs />
                  </ProtectedRoute>
                }
              />
              <Route path="settings" element={<Settings />} />
            </Route>

            {/* Catch-all redirect */}
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
