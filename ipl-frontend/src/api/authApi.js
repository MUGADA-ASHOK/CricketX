import api from './client';

export const authApi = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (username, email, password) => api.post('/auth/register', { username, email, password }),
  registerFranchise: (username, email, password) => api.post('/auth/franchise/register', { username, email, password }),
  getProfile: () => api.get('/auth/me'),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
  getPublicKey: () => api.get('/auth/public-key'),
};

