import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth, getDashboardPathForRole } from '../context/AuthContext';
import { ProtectedRoute } from '../components/common/ProtectedRoute';
import { RoleRoute } from '../components/common/RoleRoute';

import { Login } from '../pages/auth/Login';
import { Register } from '../pages/auth/Register';
import { FanDashboard } from '../pages/fan/FanDashboard';
import { MyTickets } from '../pages/fan/MyTickets';
import { NotificationsPage } from '../pages/fan/NotificationsPage';
import { FranchiseDashboard } from '../pages/franchise/FranchiseDashboard';
import { AdminDashboard } from '../pages/admin/AdminDashboard';

const RootRedirect = () => {
  const { isAuthenticated, role, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="w-12 h-12 border-4 border-amber-500 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Navigate to={getDashboardPathForRole(role)} replace />;
};

export const AppRoutes = () => {
  return (
    <Routes>
      {/* Root dynamic dispatch */}
      <Route path="/" element={<RootRedirect />} />

      {/* Public Auth */}
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* Shared Notifications */}
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <NotificationsPage />
          </ProtectedRoute>
        }
      />

      {/* Fan Routes */}
      <Route
        path="/fan"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={['USER']}>
              <FanDashboard />
            </RoleRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/fan/tickets"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={['USER']}>
              <MyTickets />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Franchise Routes */}
      <Route
        path="/franchise"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={['FRANCHISE']}>
              <FranchiseDashboard />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Admin Routes */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={['ADMIN']}>
              <AdminDashboard />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Fallback */}
      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
};

