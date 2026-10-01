import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
export default function () {
  const response = http.get(`${baseUrl}/api/v1/products`);
  check(response, { 'products response is 200': (r) => r.status === 200 });
  // One request per second keeps the smoke run far below the anonymous rate limit (ADD §7).
  sleep(1);
}
// L5 measures NFR-02 cached reads and order POST p95 < 800 ms at 20 VUs.
