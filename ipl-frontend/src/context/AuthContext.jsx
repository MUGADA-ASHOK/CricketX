import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);

export const normalizeRole = (role) => {
  if (!role) return 'USER';
  const clean = String(role).replace('ROLE_', '').toUpperCase();
  if (clean === 'ADMIN') return 'ADMIN';
  if (clean === 'FRANCHISE') return 'FRANCHISE';
  return 'USER';
};

export const getDashboardPathForRole = (role) => {
  const norm = normalizeRole(role);
  if (norm === 'ADMIN') return '/admin';
  if (norm === 'FRANCHISE') return '/franchise';
  return '/fan';
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      const storedToken = localStorage.getItem('token');
      const storedUser = localStorage.getItem('user');
      if (storedToken && storedUser) {
        setToken(storedToken);
        setUser(JSON.parse(storedUser));
      }
    } catch (e) {
      console.error('Failed to parse cached auth state:', e);
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    } finally {
      setLoading(false);
    }
  }, []);

  const login = async (email, password) => {
    const res = await authApi.login(email, password);
    const data = res?.data || res;
    const { accessToken, refreshToken, user: userData } = data;

    const normalizedUser = {
      ...userData,
      role: normalizeRole(userData?.role),
    };

    localStorage.setItem('token', accessToken);
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken);
    }
    localStorage.setItem('user', JSON.stringify(normalizedUser));

    setToken(accessToken);
    setUser(normalizedUser);

    return normalizedUser;
  };

  const register = async (username, email, password) => {
    const res = await authApi.register(username, email, password);
    return res;
  };

  const logout = async () => {
    try {
      const refreshToken = localStorage.getItem('refreshToken');
      await authApi.logout(refreshToken);
    } catch (e) {
      console.warn('Backend logout call failed or expired:', e.message);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
      setToken(null);
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        role: user ? normalizeRole(user.role) : null,
        isAuthenticated: !!token && !!user,
        loading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

