// background.js
const DEFAULT_API_CONFIG = {
  mode: 'api',
  baseUrl: 'http://52.79.82.43:8080',
  endpoint: '/api/hint',
};

const LEGACY_LOCAL_API_BASE_URLS = new Set([
  'http://localhost:8080',
  'http://127.0.0.1:8080',
]);

const DEFAULT_PROBLEM_ID = 1829;
const HINT_API_TIMEOUT_MS = 20000;
const AUTH_API_TIMEOUT_MS = 15000;
// ensure-ready/status 둘 다 이제 트리거·단발 조회일 뿐이라(실제 생성 완료까지 기다리지 않음),
// 예전처럼 생성 시간(120초)에 맞춘 긴 타임아웃이 필요 없다. 90초짜리 폴링은 사이드패널이 한다.
const PROBLEM_STATUS_REQUEST_TIMEOUT_MS = 10000;

function isJavaLanguage(language) {
  // /java/i(부분 문자열 매칭)로는 "JavaScript"도 "Java"를 포함해 지원 언어로
  // 오인된다. 단어 경계(\b)로 "Java"/"Java 17" 등만 잡고 "JavaScript"는 제외한다.
  return /\bjava\b/i.test(language || '');
}

function getLocalState(defaults) {
  return new Promise((resolve, reject) => {
    chrome.storage.local.get(defaults, (result) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      resolve(result);
    });
  });
}

function setLocalState(nextState) {
  return new Promise((resolve, reject) => {
    chrome.storage.local.set(nextState, () => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      resolve();
    });
  });
}

function buildMockAnswer(questionText, code) {
  const question = questionText || '';
  const codeLines = code ? code.split('\n').length : 0;
  const codePreview = code
    ? code.split('\n').slice(0, 3).join('\n')
    : '아직 동기화된 코드가 없습니다.';

  if (question.includes('시간복잡도')) {
    return {
      answer: `현재는 mock 응답입니다. 실제 분석 전 기준으로 보면, 질문한 로직의 시간복잡도는 반복문/재귀 깊이에 따라 달라집니다. 우선 ${codeLines}줄짜리 현재 코드에서 중첩 반복 또는 완전탐색 분기를 확인해 보세요.\n\n코드 미리보기:\n${codePreview}`,
      source: 'mock',
    };
  }

  if (question.includes('위험') || question.includes('문제')) {
    return {
      answer: `현재는 mock 응답입니다. 가장 먼저 볼 지점은 종료 조건, 인덱스 범위, 그리고 상태 초기화 여부입니다. 실제 백엔드를 붙이면 이 질문과 함께 최신 코드 전체가 전송되도록 이미 연결돼 있습니다.\n\n코드 미리보기:\n${codePreview}`,
      source: 'mock',
    };
  }

  return {
    answer: `현재는 mock 응답입니다. 질문: "${question}"\n\n백엔드가 준비되면 같은 요청 구조로 실제 힌트 응답을 받을 수 있습니다. 지금은 최신 코드 ${codeLines}줄을 기준으로 질문 흐름만 검증하도록 만들어 두었습니다.`,
    source: 'mock',
  };
}

function randomState() {
  const values = new Uint32Array(4);
  crypto.getRandomValues(values);
  return Array.from(values, (value) => value.toString(16)).join('');
}

function withTimeout(ms) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), ms);
  return { controller, timeoutId };
}

function normalizeApiBaseUrl(apiConfig) {
  const mergedConfig = { ...DEFAULT_API_CONFIG, ...(apiConfig || {}) };
  const baseUrl = (mergedConfig.baseUrl || DEFAULT_API_CONFIG.baseUrl).replace(/\/$/, '');
  return LEGACY_LOCAL_API_BASE_URLS.has(baseUrl)
    ? DEFAULT_API_CONFIG.baseUrl
    : baseUrl;
}

async function fetchJson(url, options = {}, timeoutMs = AUTH_API_TIMEOUT_MS) {
  const { controller, timeoutId } = withTimeout(timeoutMs);
  let response;
  try {
    response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('로그인 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.');
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    let detail = '';
    try {
      const errorBody = await response.json();
      if (errorBody.message) {
        detail = `: ${errorBody.message}`;
      }
    } catch (_error) {
      // ignore parse errors
    }
    throw new Error(`로그인 API 요청 실패: ${response.status}${detail}`);
  }

  return response.json();
}

function launchWebAuthFlow(details) {
  return new Promise((resolve, reject) => {
    chrome.identity.launchWebAuthFlow(details, (redirectUrl) => {
      const lastError = chrome.runtime.lastError;
      if (lastError) {
        reject(new Error(lastError.message));
        return;
      }
      if (!redirectUrl) {
        reject(new Error('카카오 로그인 리다이렉트 URL을 받지 못했습니다.'));
        return;
      }
      resolve(redirectUrl);
    });
  });
}

function getKakaoRedirectUri() {
  return chrome.identity.getRedirectURL('kakao');
}

async function requestKakaoLogin() {
  const { apiConfig } = await getLocalState({ apiConfig: DEFAULT_API_CONFIG });
  const baseUrl = normalizeApiBaseUrl(apiConfig);
  const redirectUri = getKakaoRedirectUri();
  const state = randomState();

  const authorize = await fetchJson(
    `${baseUrl}/api/auth/kakao/authorize-url?redirectUri=${encodeURIComponent(redirectUri)}&state=${encodeURIComponent(state)}`
  );
  const redirectUrl = await launchWebAuthFlow({
    url: authorize.authorizeUrl,
    interactive: true,
  });

  const redirected = new URL(redirectUrl);
  const error = redirected.searchParams.get('error');
  if (error) {
    throw new Error(`카카오 로그인이 취소되었거나 실패했습니다: ${error}`);
  }
  const returnedState = redirected.searchParams.get('state');
  if (returnedState !== state) {
    throw new Error('카카오 로그인 state 값이 일치하지 않습니다.');
  }
  const code = redirected.searchParams.get('code');
  if (!code) {
    throw new Error('카카오 인가 코드를 받지 못했습니다.');
  }

  const auth = await fetchJson(`${baseUrl}/api/auth/kakao`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, redirectUri }),
  });
  const expiresAt = Date.now() + Number(auth.expiresIn || 0) * 1000;
  const authState = {
    accessToken: auth.accessToken,
    tokenType: auth.tokenType || 'Bearer',
    expiresIn: auth.expiresIn,
    expiresAt,
    user: auth.user || null,
  };
  await setLocalState({ authState });
  return authState;
}

function buildHintRequestBody(message) {
  const hintRequest = message.hintRequest || {};
  const body = {
    problemId: hintRequest.problemId ?? DEFAULT_PROBLEM_ID,
    stage: hintRequest.stage || 'BEFORE_SOLVE',
    questionType: hintRequest.questionType || 'FREE_TEXT',
    userCode: hintRequest.userCode ?? '',
    language: hintRequest.language || 'Unknown',
    conversationHistory: Array.isArray(hintRequest.conversationHistory)
      ? hintRequest.conversationHistory
      : [],
  };

  if (hintRequest.hintLevel != null) {
    body.hintLevel = hintRequest.hintLevel;
  }
  if (hintRequest.questionType === 'BUTTON') {
    body.buttonId = hintRequest.buttonId;
  } else {
    body.questionText = hintRequest.questionText || '';
  }
  if (hintRequest.stage === 'WRONG_ANSWER' && hintRequest.submissionResult) {
    body.submissionResult = hintRequest.submissionResult;
  }
  if (Object.prototype.hasOwnProperty.call(hintRequest, 'solved')) {
    body.solved = hintRequest.solved;
  }

  return body;
}

async function requestHintFromApi(message) {
  const { apiConfig, authState } = await getLocalState({
    apiConfig: DEFAULT_API_CONFIG,
    authState: null,
  });
  const mergedConfig = { ...DEFAULT_API_CONFIG, ...(apiConfig || {}) };
  if (!mergedConfig.endpoint) {
    mergedConfig.endpoint = DEFAULT_API_CONFIG.endpoint;
  }
  const hintRequest = message.hintRequest || {};
  const questionText = hintRequest.questionText || message.question || '';

  if (mergedConfig.mode !== 'api' || !mergedConfig.baseUrl) {
    return buildMockAnswer(questionText, hintRequest.userCode || message.code);
  }

  const normalizedBaseUrl = normalizeApiBaseUrl(mergedConfig);
  const normalizedEndpoint = mergedConfig.endpoint.startsWith('/')
    ? mergedConfig.endpoint
    : `/${mergedConfig.endpoint}`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), HINT_API_TIMEOUT_MS);

  let response;
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (authState && authState.accessToken) {
      headers.Authorization = `${authState.tokenType || 'Bearer'} ${authState.accessToken}`;
    }

    response = await fetch(`${normalizedBaseUrl}${normalizedEndpoint}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(buildHintRequestBody(message)),
      signal: controller.signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('API 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.');
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    let detail = '';
    try {
      const errorBody = await response.json();
      if (errorBody.message) {
        detail = `: ${errorBody.message}`;
      }
    } catch (_error) {
      // ignore parse errors
    }
    throw new Error(`API 요청 실패: ${response.status}${detail}`);
  }

  const data = await response.json();
  return {
    answer: data.responseText || data.answer || data.message || '응답 본문에 responseText 필드가 없습니다.',
    source: 'api',
    hintLevel: data.hintLevel,
    stage: data.stage,
    suggestConceptDrill: Boolean(data.suggestConceptDrill),
  };
}

// sidepanel이 이 값을 보고 분석이 끝나기 전까지 채팅 입력을 막는다(Fix/fe#152).
// problemId를 키로 하는 맵으로 저장한다 - 단일 { problemId, status } 객체로 두면
// 처음 보는 문제 두 개를 연달아 열었을 때(둘 다 아직 pending인 상태) 나중 문제의
// pending 기록이 먼저 문제의 완료 기록에 덮어써져서, 아직 생성 중인 문제인데도
// 채팅 입력이 풀려버리는 문제가 있었다.
//
// 이 맵의 쓰기는 반드시 이 함수(그리고 이 스크립트) 하나로만 이뤄져야 한다 - get 후 set하는
// 방식이라 원자적이지 않아서, 서로 다른 problemId에 대한 쓰기가 동시에(await 사이에) 겹치면
// 나중 쓰기가 먼저 쓴 값을 통째로 덮어써 잃어버릴 수 있다(예: A→B→C→B처럼 여러 문제를 빠르게
// 넘나들 때). writeQueue로 호출을 한 줄로 세워서 항상 한 번에 하나씩만 read-modify-write가
// 끝나게 한다. 사이드패널도 직접 storage를 쓰지 않고 SET_PROBLEM_READY_STATUS 메시지로
// 이 함수를 거치게 해서, 쓰는 주체를 이 스크립트 하나로 유지한다.
let problemReadyStatusWriteQueue = Promise.resolve();

function setProblemReadyStatus(problemId, status) {
  problemReadyStatusWriteQueue = problemReadyStatusWriteQueue.catch(() => {}).then(async () => {
    const { problemReadyStatus } = await getLocalState({ problemReadyStatus: {} });
    const next = { ...(problemReadyStatus || {}), [problemId]: status };
    await setLocalState({
      problemReadyStatus: next,
    });
  });
  return problemReadyStatusWriteQueue;
}

// ensure-ready/status 요청 하나에 대한 타임아웃 적용 fetch. 둘 다 이제 트리거 또는
// 단발 조회일 뿐이라 오래 걸리지 않는다(PROBLEM_STATUS_REQUEST_TIMEOUT_MS 위 주석 참고).
async function fetchProblemApi(url, options) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), PROBLEM_STATUS_REQUEST_TIMEOUT_MS);
  try {
    // status는 계속 값이 바뀌는 값이라 브라우저 캐시에 예전 응답(예: 아직 라우트가 없던
    // 시절의 404)이 남아있으면 그걸 그대로 재사용해버린다 - 매번 실제 서버를 쳐야 한다.
    return await fetch(url, { ...options, signal: controller.signal, cache: 'no-store' });
  } finally {
    clearTimeout(timeoutId);
  }
}

// problemId를 넘겨받으면 생성을 "트리거"만 하고 바로 리턴한다 - 서버는 이미 있는 문제가
// 아니면 생성 작업을 자기 쪽 스레드풀에 던지기만 하고 즉시 202를 준다(실제 완료를 기다리지
// 않음). 트리거 직후 상태를 한 번 조회해서, 이미 준비돼 있던 흔한 경우엔 pending 플래시 없이
// 바로 ready로 넘어간다. 아직 GENERATING/NOT_STARTED면 여기서 더 기다리지 않고 pending인
// 채로 둔다 - 이 스크립트는 MV3 서비스워커라 몇십 초씩 기다리는 폴링을 넣으면 중간에 죽을 수
// 있어서, 그 몫(길게 기다렸다가 ready/error로 넘기는 것)은 사이드패널이 GET status를
// 폴링해서 대신한다(사이드패널은 일반 페이지라 서비스워커처럼 강제 종료되지 않는다).
async function ensureProblemReady(problemId) {
  if (problemId == null) {
    return;
  }

  const { apiConfig } = await getLocalState({ apiConfig: DEFAULT_API_CONFIG });
  const mergedConfig = { ...DEFAULT_API_CONFIG, ...(apiConfig || {}) };
  if (mergedConfig.mode !== 'api' || !mergedConfig.baseUrl) {
    return; // mock 모드에서는 실제 백엔드가 없으므로 호출하지 않음
  }

  const baseUrl = normalizeApiBaseUrl(mergedConfig);

  await setProblemReadyStatus(problemId, 'pending');

  try {
    const triggerResponse = await fetchProblemApi(`${baseUrl}/api/problems/${problemId}/ensure-ready`, {
      method: 'POST',
    });
    if (!triggerResponse.ok) {
      // 서버가 반환한 에러 바디(ErrorResponse.message)까지 같이 남긴다 - status 코드만으로는
      // 뭐가 터졌는지 전혀 알 수 없어서, 서버 로그 없이 이 콘솔만으로 원인을 좁힐 수 있게 한다.
      let detail = '';
      try {
        const errorBody = await triggerResponse.json();
        detail = errorBody && errorBody.message ? `: ${errorBody.message}` : '';
      } catch (_error) {
        // 바디가 JSON이 아니면 무시
      }
      console.error('[Cotea] ensure-ready 요청 실패:', triggerResponse.status, detail);
      await setProblemReadyStatus(problemId, 'error');
      return;
    }

    const statusResponse = await fetchProblemApi(`${baseUrl}/api/problems/${problemId}/status`, {
      method: 'GET',
    });
    if (statusResponse.ok) {
      const { status } = await statusResponse.json();
      if (status === 'READY') {
        console.log('[Cotea] ensure-ready 완료:', problemId);
        await setProblemReadyStatus(problemId, 'ready');
      }
      // GENERATING/NOT_STARTED면 pending 유지 - 사이드패널의 폴링이 이어받는다.
    }
  } catch (error) {
    console.error('[Cotea] ensure-ready 요청 오류:', error.message);
    await setProblemReadyStatus(problemId, 'error');
  }
}

async function requestWeeklyReport() {
  const { apiConfig, authState } = await getLocalState({
    apiConfig: DEFAULT_API_CONFIG,
    authState: null,
  });
  if (!authState || !authState.accessToken) {
    throw new Error('로그인이 필요합니다.');
  }

  const baseUrl = normalizeApiBaseUrl(apiConfig);
  return fetchJson(`${baseUrl}/api/reports/me/weekly`, {
    method: 'GET',
    headers: {
      Authorization: `${authState.tokenType || 'Bearer'} ${authState.accessToken}`,
    },
  });
}

chrome.sidePanel
  .setPanelBehavior({ openPanelOnActionClick: true })
  .catch((error) => console.error(error));

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'CODE_CHANGED') {
    // 에디터에서 실시간으로 감지된 변경 - 동기화된 코드와 달라졌는지, 언어가 지원 대상인지 표시
    getLocalState({ latestCode: '' })
      .then(({ latestCode }) => {
        const codeDirty = Boolean(message.code) && message.code !== latestCode;
        console.log('[Cotea] CODE_CHANGED 수신:', message.code ? message.code.length : 0, '자, codeDirty=', codeDirty);
        const nextState = { codeDirty };
        if (message.problemId != null) {
          nextState.problemId = message.problemId;
        }
        if (message.problemTitle) {
          nextState.problemTitle = message.problemTitle;
        }
        if (Object.prototype.hasOwnProperty.call(message, 'solved')) {
          nextState.problemSolved = message.solved;
        }
        if (message.language) {
          nextState.currentLanguage = message.language;
          nextState.languageNotSupported = !isJavaLanguage(message.language);
        }
        chrome.storage.local.set(nextState);
      })
      .catch((error) => {
        console.error('[Cotea] CODE_CHANGED 상태 조회 실패:', error.message);
      });
  }

  if (message.type === 'ENSURE_PROBLEM_READY') {
    // fire-and-forget: content.js는 응답을 기다리지 않음
    ensureProblemReady(message.problemId);
  }

  if (message.type === 'SET_PROBLEM_READY_STATUS') {
    // 사이드패널이 GET status 폴링 결과(ready/error)를 반영할 때 이걸 거친다 -
    // setProblemReadyStatus 위 주석 참고: 쓰기는 항상 이 스크립트 하나로만 모아야 한다.
    // sendResponse를 안 부르고 return true도 안 하면 리스너가 동기적으로 끝나자마자
    // 메시지 포트가 닫혀서, 호출한 쪽(sidepanel.js의 sendRuntimeMessage)이 "message port
    // closed before a response was received"로 실패했다. pollProblemStatus가 READY를
    // 받고도 이 예외 때문에 return을 못 하고 계속 돌다가, 결국 60초 타임아웃으로 방금 쓴
    // 'ready'를 'error'로 덮어써버리는 버그가 있었다.
    setProblemReadyStatus(message.problemId, message.status)
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }

  if (message.type === 'GRADING_RESULT') {
    // detectedAt으로 매번 값이 바뀌게 해서, 연속으로 같은 결과(예: 오답→오답)가
    // 나와도 storage.onChanged가 매번 발생하도록 한다.
    // passed는 엄격히 true일 때만 true로 취급한다 (Boolean(message.passed)는
    // "false" 같은 문자열도 truthy라 true가 돼버림). 통과(passed=true)했으면
    // failureReason은 항상 null로 강제해 저장 데이터가 자기모순되지 않게 한다.
    const passed = message.passed === true;
    setLocalState({
      gradingResult: {
        passed,
        failureReason: passed ? null : (message.failureReason ?? null),
        source: message.source === 'run' ? 'run' : 'submit',
        problemId: message.problemId ?? null,
        detectedAt: Date.now(),
      },
    }).catch((error) => {
      console.error('[Cotea] GRADING_RESULT 저장 실패:', error.message);
    });
  }

  if (message.type === 'CLEAR_STALE_GRADING_RESULT') {
    // 새로고침 등으로 콘솔에 결과가 없는 상태로 재진입했을 때 content.js가 보낸다.
    // storage에 남아있는 이전 결과가 지금 문제 것일 때만 지운다 - 다른 문제/탭의
    // 결과를 실수로 지우지 않기 위함이다.
    getLocalState({ gradingResult: null })
      .then(({ gradingResult }) => {
        if (gradingResult && gradingResult.problemId === message.problemId) {
          return setLocalState({ gradingResult: null });
        }
      })
      .catch((error) => {
        console.error('[Cotea] gradingResult 초기화 실패:', error.message);
      });
  }

  if (message.type === 'GET_PANEL_STATE') {
    getLocalState({
      latestCode: '',
      languageNotSupported: false,
      currentLanguage: 'Java',
      problemId: null,
      problemTitle: null,
      problemSolved: null,
      apiConfig: DEFAULT_API_CONFIG,
      authState: null,
      codeDirty: false,
      gradingResult: null,
      problemReadyStatus: {},
    })
      .then((state) => sendResponse(state))
      .catch((error) => {
        console.error('[Cotea] GET_PANEL_STATE 조회 실패:', error.message);
        sendResponse({ error: error.message });
      });
    return true;
  }

  if (message.type === 'LOGIN_KAKAO') {
    requestKakaoLogin()
      .then((authState) => sendResponse({ ok: true, authState }))
      .catch((error) => {
        console.error('[Cotea] 카카오 로그인 실패:', error.message);
        sendResponse({ ok: false, error: error.message });
      });
    return true;
  }

  if (message.type === 'GET_KAKAO_REDIRECT_URI') {
    try {
      sendResponse({ ok: true, redirectUri: getKakaoRedirectUri() });
    } catch (error) {
      sendResponse({ ok: false, error: error.message });
    }
    return false;
  }

  if (message.type === 'LAUNCH_KAKAO_AUTH') {
    launchWebAuthFlow({
      url: message.authorizeUrl,
      interactive: true,
    })
      .then((redirectUrl) => sendResponse({ ok: true, redirectUrl }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }

  if (message.type === 'LOGOUT') {
    setLocalState({ authState: null })
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }

  if (message.type === 'GET_WEEKLY_REPORT') {
    requestWeeklyReport()
      .then((report) => sendResponse({ ok: true, report }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }

  if (message.type === 'SET_API_CONFIG') {
    const nextConfig = { ...DEFAULT_API_CONFIG, ...(message.payload || {}) };
    setLocalState({ apiConfig: nextConfig })
      .then(() => sendResponse({ ok: true }))
      .catch((error) => {
        console.error('[Cotea] SET_API_CONFIG 저장 실패:', error.message);
        sendResponse({ ok: false, error: error.message });
      });
    return true;
  }

  if (message.type === 'ASK_AI') {
    requestHintFromApi(message)
      .then((result) => sendResponse(result))
      .catch((error) => {
        console.error('[Cotea] ASK_AI 요청 실패:', error);
        sendResponse({
          answer: '오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
          source: 'error',
        });
      });

    return true; // 비동기 응답이므로 반드시 true 리턴 (이거 빼먹으면 응답 씹힘)
  }

  if (message.type === 'SYNC_CODE') {
    console.log('[Cotea] SYNC_CODE 요청 수신');

    // 현재 활성 탭 또는 프로그래머스 탭에서 코드를 요청
    chrome.tabs.query({}, (allTabs) => {
      console.log('[Cotea] 전체 탭 수:', allTabs.length);

      const programmersTabs = allTabs.filter(
        (tab) => tab.url && tab.url.includes('school.programmers.co.kr')
      );
      const activeProgrammersTab = programmersTabs.find((tab) => tab.active);
      const targetTab = activeProgrammersTab || programmersTabs[0] || null;

      if (!targetTab) {
        console.log('[Cotea] 프로그래머스 탭을 찾을 수 없음');
        sendResponse({
          error: '프로그래머스 페이지를 찾을 수 없습니다. 프로그래머스 문제 탭을 먼저 열어주세요.'
        });
        return;
      }

      console.log('[Cotea] 대상 탭 ID:', targetTab.id, 'URL:', targetTab.url);

      // 탭에서 코드 가져오기
      chrome.tabs.sendMessage(targetTab.id, { type: 'GET_CODE' }, (response) => {
        if (chrome.runtime.lastError) {
          console.log('[Cotea] 메시지 전송 오류:', chrome.runtime.lastError.message);
          sendResponse({
            error: 'content.js와 통신할 수 없습니다. 프로그래머스 탭 새로고침 후 다시 시도해주세요.'
          });
        } else if (response && typeof response.code === 'string') {
          console.log('[Cotea] 코드 수신 완료:', response.code.length, '자');

          const language = response.language || 'Unknown';
          const isJava = isJavaLanguage(language);

          // 받은 코드를 저장
          const nextState = {
            latestCode: response.code,
            languageNotSupported: !isJava,
            currentLanguage: language,
            codeDirty: false,
          };
          if (response.problemId != null) {
            nextState.problemId = response.problemId;
          }
          if (response.problemTitle) {
            nextState.problemTitle = response.problemTitle;
          }
          if (Object.prototype.hasOwnProperty.call(response, 'solved')) {
            nextState.problemSolved = response.solved;
          }
          chrome.storage.local.set(nextState);

          sendResponse({
            ...response,
            language,
            languageNotSupported: !isJava,
            warning: isJava ? '' : `현재 선택 언어(${language})는 미지원입니다. Java로 바꿔주세요.`,
          });
        } else if (response && response.error) {
          console.log('[Cotea] content.js 오류:', response.error);
          sendResponse(response);
        } else {
          console.log('[Cotea] 응답이 없거나 코드가 없음');
          sendResponse({
            error: '코드를 가져올 수 없습니다.'
          });
        }
      });
    });

    return true; // 비동기 응답이므로 반드시 true 리턴
  }
});
