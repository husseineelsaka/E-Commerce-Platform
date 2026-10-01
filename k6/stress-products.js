import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// Stress: ramp catalogue reads until the platform breaks (ADD §8). Run with the anonymous rate limit raised
// (GATEWAY_ANON_REPLENISH_RATE / GATEWAY_ANON_BURST), otherwise the first errors are only 429s.
// PEAK sets the last stage's arrival rate; each stage lasts one minute and is tagged so the summary shows
// latency and errors per stage.
const peak = Number(__ENV.PEAK || 2000);
const stageRates = [0.125, 0.25, 0.5, 0.75, 1].map((f) => Math.round(peak * f));
const stageTargets = {};
stageRates.forEach((rate, i) => {
  // Not a pass/fail gate: the summary records where latency and errors start to climb.
  stageTargets[`http_req_duration{stage:${i + 1}-${rate}rps}`] = [{ threshold: 'p(95)<500', abortOnFail: false }];
  stageTargets[`http_req_failed{stage:${i + 1}-${rate}rps}`] = [{ threshold: 'rate<0.01', abortOnFail: false }];
});

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: stageRates[0],
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: Number(__ENV.MAX_VUS || 1500),
      // Each stage ramps to its rate within 10 s and holds it for 50 s.
      stages: stageRates.flatMap((rate) => [{ target: rate, duration: '10s' }, { target: rate, duration: '50s' }]),
    },
  },
  thresholds: stageTargets,
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const stage = Math.min(Math.floor(exec.instance.currentTestRunDuration / 60000), stageRates.length - 1);
  const id = 1 + Math.floor(Math.random() * 19);
  const response = http.get(`${baseUrl}/api/v1/products/${id}`, { tags: { stage: `${stage + 1}-${stageRates[stage]}rps` } });
  check(response, { 'detail 200': (r) => r.status === 200 });
}
