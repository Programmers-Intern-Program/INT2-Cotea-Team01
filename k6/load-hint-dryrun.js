/**
 * 힌트 dryRun 부하 (프롬프트 조립까지 — Claude 호출 없음)
 *
 *   k6 run k6/load-hint-dryrun.js
 *   VUS=5 DURATION=30s k6 run k6/load-hint-dryrun.js
 *
 * 주의: dryRun=false 로 바꾸면 실제 LLM 비용·쿼터가 나갑니다. 이 스크립트는 dryRun 고정.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, jsonHeaders, hintDryRunBody, okStatus } from './lib/common.js';

const failRate = new Rate('hint_dryrun_fail');
const latency = new Trend('hint_dryrun_latency', true);

const vus = Number(__ENV.VUS || 5);
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    hint_dryrun: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
    hint_dryrun_fail: ['rate<0.05'],
  },
};

export default function () {
  const body = hintDryRunBody({
    questionText: `부하테스트 dryRun ${__VU}-${__ITER}`,
  });
  const res = http.post(`${baseUrl()}/api/hint`, JSON.stringify(body), {
    headers: jsonHeaders(),
    tags: { name: 'hint_dryrun' },
  });
  latency.add(res.timings.duration);
  const ok = check(res, {
    'hint dryRun 200': (r) => okStatus(r),
    'dryRun flag': (r) => {
      try {
        return JSON.parse(r.body).dryRun === true;
      } catch (_) {
        return false;
      }
    },
  });
  failRate.add(!ok);
  sleep(Number(__ENV.THINK_TIME || 0.3));
}
