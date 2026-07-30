/**
 * 추천 API 부하 (LLM 없음 — 상대적으로 저렴/안전)
 *
 *   k6 run k6/load-recommend.js
 *   VUS=20 DURATION=1m k6 run k6/load-recommend.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, problemId, jsonHeaders, okStatus } from './lib/common.js';

const failRate = new Rate('recommend_fail');
const latency = new Trend('recommend_latency', true);

const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    recommend: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    recommend_fail: ['rate<0.05'],
  },
};

export default function () {
  const url = `${baseUrl()}/api/recommend?problemId=${problemId()}&limit=3`;
  const res = http.get(url, { headers: jsonHeaders(), tags: { name: 'recommend' } });
  latency.add(res.timings.duration);
  const ok = check(res, {
    'recommend 200': (r) => okStatus(r),
  });
  failRate.add(!ok);
  sleep(Number(__ENV.THINK_TIME || 0.2));
}
