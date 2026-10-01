import http from 'k6/http';
import { check } from 'k6';

// Load: anonymous catalogue reads through the Gateway at a fixed arrival rate (NFR-02, NFR-03).
// 60 req/s from one IP stays below the anonymous limit of 100 req/s (ADD §7).
export const options = {
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 60),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    'http_req_duration{name:product}': ['p(95)<200'],
    'http_req_duration{name:list}': ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=50'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const id = 1 + Math.floor(Math.random() * 19);
  const detail = http.get(`${baseUrl}/api/v1/products/${id}`, { tags: { name: 'product' } });
  check(detail, { 'detail 200': (r) => r.status === 200, 'not rate limited': (r) => r.status !== 429 });
  if (Math.random() < 0.25) {
    const page = Math.floor(Math.random() * 4);
    check(http.get(`${baseUrl}/api/v1/products?page=${page}&size=5`, { tags: { name: 'list' } }),
      { 'list 200': (r) => r.status === 200 });
  }
}
