import http from 'k6/http';

// Fixed arrival rate of anonymous product reads, used for the before/after comparison in docs/PERFORMANCE-REPORT.md.
// Run with the anonymous rate limit raised (see README), e.g. RATE=800 DURATION=120s.
export const options = {
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 800),
      timeUnit: '1s',
      duration: __ENV.DURATION || '120s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  http.get(`${baseUrl}/api/v1/products/${1 + Math.floor(Math.random() * 19)}`);
}
