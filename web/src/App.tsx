import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';

// Pages
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Incidents } from './pages/Incidents';
import { IncidentDetails } from './pages/IncidentDetails';
import { LiveMap } from './pages/LiveMap';
import { ResponseTeams } from './pages/ResponseTeams';
import { PublicWarnings } from './pages/PublicWarnings';
import { Analytics } from './pages/Analytics';
import { AuditLogs } from './pages/AuditLogs';
import { Settings } from './pages/Settings';

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
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
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
