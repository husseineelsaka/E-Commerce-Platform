import http from 'k6/http';
import { check } from 'k6';

// Demo of FR-13: 400 anonymous requests/s for 5 s from one IP against the 100 req/s limit (burst 200).
// The Gateway serves the first burst and then answers 429.
export const options = {
  scenarios: {
    burst: { executor: 'constant-arrival-rate', rate: 400, timeUnit: '1s', duration: '5s', preAllocatedVUs: 200, maxVUs: 400 },
  },
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/products/1`);
  check(response, {
    'served (200)': (r) => r.status === 200,
    'rate limited (429)': (r) => r.status === 429,
  });
}
