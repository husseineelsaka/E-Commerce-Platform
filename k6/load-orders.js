import http from 'k6/http';
import { check, sleep } from 'k6';
import { customerToken } from './lib/auth.js';

// Load: 20 virtual users place orders (NFR-02: POST /api/v1/orders P95 < 800 ms at 20 VUs).
// sleep(1) keeps one customer at <= 20 req/s, the signed-in rate limit (ADD §7).
export const options = {
  vus: 20,
  duration: __ENV.DURATION || '2m',
  thresholds: {
    'http_req_duration{name:place-order}': ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  return { token: customerToken() };
}

export default function (data) {
  const productId = 1 + Math.floor(Math.random() * 19);
  const response = http.post(`${baseUrl}/api/v1/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }),
    { headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' }, tags: { name: 'place-order' } });
  check(response, { 'order accepted 201': (r) => r.status === 201, 'not rate limited': (r) => r.status !== 429 });
  sleep(1);
}
