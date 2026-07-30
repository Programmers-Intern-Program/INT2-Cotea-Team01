/**
 * 혼합 부하: health + recommend + hint dryRun
 *
 *   k6 run k6/load-mixed.js
 *   VUS=10 DURATION=1m k6 run k6/load-mixed.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { baseUrl, problemId, jsonHeaders, hintDryRunBody, okStatus } from './lib/common.js';

const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    mixed: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const root = baseUrl();
  const pid = problemId();
  const roll = Math.random();

  if (roll < 0.2) {
    const res = http.get(`${root}/`, { tags: { name: 'health' } });
    check(res, { 'health 200': (r) => okStatus(r) });
  } else if (roll < 0.55) {
    const res = http.get(`${root}/api/recommend?problemId=${pid}&limit=3`, {
      headers: jsonHeaders(),
      tags: { name: 'recommend' },
    });
    check(res, { 'recommend 200': (r) => okStatus(r) });
  } else {
    const res = http.post(`${root}/api/hint`, JSON.stringify(hintDryRunBody()), {
      headers: jsonHeaders(),
      tags: { name: 'hint_dryrun' },
    });
    check(res, { 'hint dryRun 200': (r) => okStatus(r) });
  }

  sleep(Number(__ENV.THINK_TIME || 0.25));
}
