// background.js
const DEFAULT_API_CONFIG = {
  mode: 'mock',
  baseUrl: '',
  endpoint: '/api/hint',
};

function getLocalState(defaults) {
  return new Promise((resolve) => {
    chrome.storage.local.get(defaults, resolve);
  });
}

function setLocalState(nextState) {
  return new Promise((resolve) => {
    chrome.storage.local.set(nextState, resolve);
  });
}

function buildMockAnswer(question, code) {
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

async function requestHintFromApi(message) {
  const { apiConfig } = await getLocalState({ apiConfig: DEFAULT_API_CONFIG });
  const mergedConfig = { ...DEFAULT_API_CONFIG, ...(apiConfig || {}) };

  if (mergedConfig.mode !== 'api' || !mergedConfig.baseUrl) {
    return buildMockAnswer(message.question, message.code);
  }

  const normalizedBaseUrl = mergedConfig.baseUrl.replace(/\/$/, '');
  const normalizedEndpoint = mergedConfig.endpoint.startsWith('/')
    ? mergedConfig.endpoint
    : `/${mergedConfig.endpoint}`;

  const response = await fetch(`${normalizedBaseUrl}${normalizedEndpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      code: message.code,
      question: message.question,
      source: 'chrome-extension',
    }),
  });

  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status}`);
  }

  const data = await response.json();
  return {
    answer: data.answer || data.message || '응답 본문에 answer 필드가 없습니다.',
    source: 'api',
  };
}

chrome.sidePanel
  .setPanelBehavior({ openPanelOnActionClick: true })
  .catch((error) => console.error(error));

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'LANGUAGE_NOT_SUPPORTED') {
    // 지원하지 않는 언어 메시지 저장
    chrome.storage.local.set({
      languageNotSupported: true,
      currentLanguage: message.language,
      latestCode: ''
    });
  }

  if (message.type === 'CODE_UPDATED') {
    // 최신 코드를 저장해두고 side panel이 필요할 때 꺼내 쓸 수 있게
    chrome.storage.local.set({
      latestCode: message.code,
      languageNotSupported: false,
      currentLanguage: message.language || 'Java'
    });
  }

  if (message.type === 'GET_PANEL_STATE') {
    getLocalState({
      latestCode: '',
      languageNotSupported: false,
      currentLanguage: 'Java',
      apiConfig: DEFAULT_API_CONFIG
    })
      .then((state) => sendResponse(state));
    return true;
  }

  if (message.type === 'SET_API_CONFIG') {
    const nextConfig = { ...DEFAULT_API_CONFIG, ...(message.payload || {}) };
    setLocalState({ apiConfig: nextConfig })
      .then(() => sendResponse({ ok: true }));
    return true;
  }

  if (message.type === 'ASK_AI') {
    requestHintFromApi(message)
      .then((result) => sendResponse(result))
      .catch((error) => {
        sendResponse({
          answer: `응답 생성 중 오류가 발생했습니다. ${error.message}`,
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
        } else if (response && response.code) {
          console.log('[Cotea] 코드 수신 완료:', response.code.length, '자');

          const language = response.language || 'Unknown';
          const isJava = /java/i.test(language);

          // 받은 코드를 저장
          chrome.storage.local.set({
            latestCode: response.code,
            languageNotSupported: !isJava,
            currentLanguage: language
          });

          sendResponse({
            ...response,
            warning: isJava ? '' : `현재 선택 언어(${language})는 미지원입니다. Java로 바꿔주세요.`
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