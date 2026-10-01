import http from 'k6/http';
import { check, sleep } from 'k6';
import { customerToken } from './lib/auth.js';

// Smoke: one virtual user touches every public and customer endpoint once per second (ADD §8).
export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate==1.0'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  return { token: customerToken() };
}

export default function (data) {
  const auth = { headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' } };
  check(http.get(`${baseUrl}/api/v1/products?page=0&size=10`), { 'list products 200': (r) => r.status === 200 });
  check(http.get(`${baseUrl}/api/v1/products/1`), { 'product detail 200': (r) => r.status === 200 });
  const order = http.post(`${baseUrl}/api/v1/orders`, JSON.stringify({ items: [{ productId: 1, quantity: 1 }] }), auth);
  check(order, { 'place order 201': (r) => r.status === 201 });
  check(http.get(`${baseUrl}/api/v1/orders?page=0&size=5`, auth), { 'own orders 200': (r) => r.status === 200 });
  // One iteration per second keeps the smoke run far below every rate limit (ADD §7).
  sleep(1);
}
