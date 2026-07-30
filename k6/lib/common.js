/**
 * 공통 설정 / 페이로드 / 체크 헬퍼
 *
 * 환경변수:
 *   BASE_URL     기본 http://localhost:8080
 *   PROBLEM_ID   기본 1829
 *   AUTH_TOKEN   선택 — "Bearer ..." 또는 raw JWT (있으면 Authorization 헤더 추가)
 */

export function baseUrl() {
  return (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
}

export function problemId() {
  const raw = __ENV.PROBLEM_ID || '1829';
  return Number(raw);
}

export function authHeaders() {
  const token = __ENV.AUTH_TOKEN;
  if (!token) {
    return {};
  }
  const value = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  return { Authorization: value };
}

export function jsonHeaders(extra = {}) {
  return Object.assign(
    { 'Content-Type': 'application/json', Accept: 'application/json' },
    authHeaders(),
    extra
  );
}

/** LLM 비용 없는 dryRun 힌트 바디 */
export function hintDryRunBody(overrides = {}) {
  return Object.assign(
    {
      problemId: problemId(),
      stage: 'SOLVING',
      hintLevel: 1,
      questionType: 'FREE_TEXT',
      questionText: '이 문제 접근 방향이 맞나요?',
      language: 'Java',
      dryRun: true,
    },
    overrides
  );
}

/** 실제 LLM 호출 — 부하 테스트 기본값으로는 쓰지 말 것 */
export function hintLiveBody(overrides = {}) {
  return Object.assign(hintDryRunBody({ dryRun: false }), overrides);
}

export function okStatus(res, allowed = [200]) {
  return allowed.includes(res.status);
}
