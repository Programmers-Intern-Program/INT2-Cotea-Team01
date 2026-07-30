/**
 * 스모크: 서버가 살아 있고 핵심 API가 응답하는지 빠르게 확인
 *
 *   k6 run k6/smoke.js
 *   BASE_URL=http://localhost:8080 k6 run k6/smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { baseUrl, problemId, jsonHeaders, hintDryRunBody, okStatus } from './lib/common.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

export default function () {
  const root = baseUrl();
  const pid = problemId();

  const health = http.get(`${root}/`);
  check(health, {
    'GET / → 200': (r) => okStatus(r),
    'GET / has service': (r) => String(r.body).includes('cotea-backend'),
  });

  const recommend = http.get(`${root}/api/recommend?problemId=${pid}&limit=3`, {
    headers: jsonHeaders(),
  });
  check(recommend, {
    'GET /api/recommend → 200': (r) => okStatus(r),
    'recommend has sourceProblemId': (r) => {
      try {
        return JSON.parse(r.body).sourceProblemId === pid;
      } catch (_) {
        return false;
      }
    },
  });

  const hint = http.post(`${root}/api/hint`, JSON.stringify(hintDryRunBody()), {
    headers: jsonHeaders(),
  });
  check(hint, {
    'POST /api/hint dryRun → 200': (r) => okStatus(r),
    'hint dryRun true': (r) => {
      try {
        return JSON.parse(r.body).dryRun === true;
      } catch (_) {
        return false;
      }
    },
  });

  sleep(0.3);
}
