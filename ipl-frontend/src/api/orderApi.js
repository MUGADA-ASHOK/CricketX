import api from './client';

export const orderApi = {
  // Inventory & Seats
  getInventory: (matchId) => api.get(`/orders/inventory/${matchId}`),
  getSeatMap: (matchId) => api.get(`/orders/matches/${matchId}/seats`),

  // Two-Phase Reservations (supports Idempotency-Key)
  reserveTickets: (data, idempotencyKey) =>
    api.post('/orders/reserve', data, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }),

  reserveSpecificSeats: (data, idempotencyKey) =>
    api.post('/orders/reserve-seats', data, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }),

  // Payment Phase
  createRazorpayOrder: (orderId) => api.post(`/orders/${orderId}/razorpay/create`),
  confirmPayment: (orderId, paymentData, idempotencyKey) =>
    api.post(`/orders/${orderId}/pay`, paymentData, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }),

  // Orders & Cancellations
  getOrder: (orderId) => api.get(`/orders/${orderId}`),
  getMyOrders: () => api.get('/orders/my-orders'),
  cancelOrder: (orderId) => api.post(`/orders/${orderId}/cancel`),

  // Digital Tickets & Turnstile Scanner
  getMyTickets: () => api.get('/orders/tickets/my-tickets'),
  validateTurnstile: (qrCodeData, gate) =>
    api.post('/orders/tickets/validate-turnstile', { qrCodeData, gate }),
};

