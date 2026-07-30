/**
 * 실제 LLM 힌트 호출 — 비용 주의. 기본은 VU 1 / iteration 1.
 *
 *   CONFIRM_LIVE=1 k6 run k6/load-hint-live.js
 *   CONFIRM_LIVE=1 VUS=2 DURATION=10s k6 run k6/load-hint-live.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { baseUrl, jsonHeaders, hintLiveBody, okStatus } from './lib/common.js';

if (__ENV.CONFIRM_LIVE !== '1') {
  throw new Error(
    '실제 LLM 호출 스크립트입니다. 비용이 발생합니다. CONFIRM_LIVE=1 을 주고 실행하세요.'
  );
}

const vus = Number(__ENV.VUS || 1);
const duration = __ENV.DURATION || '5s';

export const options = {
  scenarios: {
    hint_live: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.2'],
    http_req_duration: ['p(95)<30000'],
  },
};

export default function () {
  const res = http.post(
    `${baseUrl()}/api/hint`,
    JSON.stringify(
      hintLiveBody({
        questionText: '0인 칸은 어떻게 처리해야 하나요?',
        hintLevel: 1,
      })
    ),
    {
      headers: jsonHeaders(),
      tags: { name: 'hint_live' },
      timeout: '60s',
    }
  );
  check(res, {
    'hint live 200': (r) => okStatus(r),
    'has responseText': (r) => {
      try {
        const body = JSON.parse(r.body);
        return typeof body.responseText === 'string' && body.responseText.length > 0;
      } catch (_) {
        return false;
      }
    },
  });
  sleep(Number(__ENV.THINK_TIME || 1));
}
