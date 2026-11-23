import api from './client';

export const eventApi = {
  // Stadiums
  getStadiums: () => api.get('/events/stadiums'),
  createStadium: (data) => api.post('/events/stadiums', data),

  // Franchises
  getFranchises: () => api.get('/events/franchises'),
  getFranchise: (id) => api.get(`/events/franchises/${id}`),
  createFranchise: (data) => api.post('/events/franchises', data),
  updateFranchise: (id, data) => api.put(`/events/franchises/${id}`, data),

  // Matches
  getMatches: () => api.get('/events/matches'),
  getMatch: (id) => api.get(`/events/matches/${id}`),
  createMatch: (data) => api.post('/events/matches', data),
  configureMatch: (matchId, data) => api.post(`/events/matches/${matchId}/configuration`, data),
  approveMatch: (matchId) => api.post(`/events/matches/${matchId}/approve`),
};

