// content.js
// 프로그래머스 코드 에디터에서 사용자 요청에 의해 코드를 읽어온다

function getCodeMirrorInstance() {
  const editorEl = document.querySelector('.CodeMirror');
  if (editorEl && editorEl.CodeMirror) {
    return editorEl.CodeMirror;
  }
  return null;
}

function isEditorPresent() {
  // 코드가 빈 문자열인 것("에디터는 찾았지만 내용이 없음")과
  // 에디터 자체를 못 찾은 것을 구분하기 위한 존재 여부 체크
  return Boolean(
    document.querySelector('.CodeMirror')
    || document.querySelector('textarea#code')
    || document.querySelector('input[data-type="code"]')
  );
}

function decodeHtml(htmlText) {
  const textarea = document.createElement('textarea');
  textarea.innerHTML = htmlText;
  return textarea.value;
}

function getFallbackCode() {
  // 프로그래머스는 hidden textarea/input에도 최신 코드가 반영되는 경우가 있음
  const textareaCode = document.querySelector('textarea#code');
  if (textareaCode && textareaCode.value) {
    return decodeHtml(textareaCode.value);
  }

  const hiddenCodeInput = document.querySelector('input[data-type="code"]');
  if (hiddenCodeInput && hiddenCodeInput.value) {
    return decodeHtml(hiddenCodeInput.value);
  }

  return '';
}

function getProblemTitle() {
  // 문제 본문 영역에서 흔히 쓰이는 제목 엘리먼트 후보들 (페이지 구조가 바뀌면 갱신 필요)
  const titleSelectors = [
    '.challenge-title',
    '.list-header h4',
    '.result-content h4',
    'div.markdown h1',
    'article h1',
  ];

  for (const selector of titleSelectors) {
    const el = document.querySelector(selector);
    if (el && el.textContent.trim()) {
      return el.textContent.trim();
    }
  }

  // DOM에서 못 찾으면 탭 제목에서 유추 (보통 "문제명 - 코딩테스트 연습 | 프로그래머스 스쿨" 형태)
  const rawTitle = document.title || '';
  const cleaned = rawTitle.split(/[-|·]/)[0].trim();
  return cleaned || '';
}

function parseProblemId() {
  const url = window.location.href;
  const patterns = [
    /\/lessons\/(\d+)/,
    /\/challenges\/(\d+)/,
    /[?&]problem_id=(\d+)/,
  ];

  for (const pattern of patterns) {
    const match = url.match(pattern);
    if (match) {
      return Number.parseInt(match[1], 10);
    }
  }

  return null;
}

function getCurrentLanguage() {
  // 프로그래머스 언어 선택 버튼에서 현재 선택된 언어 확인
  let langButton = document.querySelector('div.dropdown-language button.btn.btn-sm.btn-dark');
  if (!langButton) {
    langButton = document.querySelector('#tour7 button');
  }
  if (!langButton) {
    langButton = document.querySelector('button.btn.btn-sm.btn-dark.dropdown-toggle');
  }

  if (langButton) {
    return langButton.textContent.replace(/\s+/g, ' ').trim();
  }

  const challengeContent = document.querySelector('.challenge-content[data-language]');
  if (challengeContent && challengeContent.dataset.language) {
    return challengeContent.dataset.language;
  }

  return 'Unknown';
}

function getVisibleEditorText() {
  // CodeMirror 인스턴스를 못 찾았을 때만 쓰는 최후의 수단.
  // CodeMirror는 뷰포트에 보이는 줄만 DOM에 렌더링(가상 스크롤)하므로
  // 이 값은 스크롤 상태에 따라 전체 문서가 아닐 수 있다.
  const editorEl = document.querySelector('.CodeMirror');
  if (!editorEl) {
    return null;
  }

  const lines = editorEl.querySelectorAll('.CodeMirror-line');
  if (!lines.length) {
    return null;
  }

  return Array.from(lines)
    .map((line) => line.textContent.replace(/​/g, ''))
    .join('\n');
}

function getBestAvailableCode() {
  // CodeMirror 인스턴스가 있으면 항상 getValue()를 최우선으로 쓴다.
  // getValue()는 뷰(스크롤/가상 렌더링)와 무관하게 내부 문서 모델을 그대로
  // 반환하므로 항상 정확한 전체 코드다. 화면 DOM을 직접 읽는 방식은
  // 스크롤 중 일부 줄만 그려진 상태를 잡아버릴 수 있어 인스턴스를 찾지
  // 못한 예외 상황에서만 폴백으로 사용한다.
  const editorInstance = getCodeMirrorInstance();
  if (editorInstance) {
    return editorInstance.getValue();
  }

  const visibleText = getVisibleEditorText();
  if (visibleText !== null) {
    return visibleText;
  }

  return getFallbackCode();
}

let lastKnownCode = null;
let observedEditorEl = null;
let editorObserver = null;
let changeDebounceTimer = null;
let contextInvalidated = false;
let attachObserverIntervalId = null;
let observedGradingContainerEl = null;
let gradingObserver = null;
const processedGradingHeadings = new WeakSet();

function isExtensionContextValid() {
  return typeof chrome !== 'undefined' && !!chrome.runtime && !!chrome.runtime.id;
}

function handleContextInvalidated() {
  if (contextInvalidated) {
    return;
  }
  contextInvalidated = true;
  console.log('[Cotea Content] 익스텐션이 리로드되어 이 탭의 연결이 끊겼습니다. 감지를 중단합니다 (페이지를 새로고침하면 복구됩니다).');

  if (editorObserver) {
    editorObserver.disconnect();
  }
  if (gradingObserver) {
    gradingObserver.disconnect();
  }
  clearTimeout(changeDebounceTimer);
  if (attachObserverIntervalId) {
    clearInterval(attachObserverIntervalId);
  }
}

function notifyIfChanged() {
  if (contextInvalidated || !isEditorPresent()) {
    return;
  }

  const code = getBestAvailableCode();

  if (code === lastKnownCode) {
    return;
  }

  lastKnownCode = code;
  const language = getCurrentLanguage();

  if (!isExtensionContextValid()) {
    handleContextInvalidated();
    return;
  }

  console.log('[Cotea Content] 실시간 변경 감지:', code.length, '자,', language);
  try {
    chrome.runtime.sendMessage({
      type: 'CODE_CHANGED',
      code,
      language,
      problemId: parseProblemId(),
      problemTitle: getProblemTitle(),
    });
  } catch (_error) {
    handleContextInvalidated();
  }
}

function attachEditorObserver() {
  if (contextInvalidated) {
    return;
  }

  const editorEl = document.querySelector('.CodeMirror');
  if (!editorEl || editorEl === observedEditorEl) {
    return;
  }

  observedEditorEl = editorEl;
  if (editorObserver) {
    editorObserver.disconnect();
  }

  editorObserver = new MutationObserver(() => {
    clearTimeout(changeDebounceTimer);
    changeDebounceTimer = setTimeout(notifyIfChanged, 400);
  });
  editorObserver.observe(editorEl, { childList: true, subtree: true, characterData: true });

  console.log('[Cotea Content] 코드 에디터 DOM 관찰 시작');
  notifyIfChanged();
}

function findGradingContainer() {
  return document.querySelector('#output') || document.querySelector('.console-content');
}

function isResultHeadingText(text) {
  // "채점 결과"(제출 후 채점하기) / "테스트 결과"(코드 실행, 샘플 테스트케이스만 - 실제 DOM
  // 문구는 "테스트 결과 (~˘▽˘)~") 둘 다 감지 대상. (2026-07-20 실기기 DOM 확인)
  return text.includes('채점 결과') || text.includes('테스트 결과');
}

function getResultSource(headingText) {
  return headingText.includes('채점 결과') ? 'submit' : 'run';
}

function parseGradingPassed(headingEl) {
  // 콘솔 블록 텍스트 전체에서 합/불 판정 신호를 찾는다.
  // - 제출 후 채점: "합계: X / Y"
  // - 코드 실행(샘플 테스트케이스만): "N개 중 M개 성공"
  // 컴파일 에러 등으로 두 신호 모두 없는 경우도 불합격으로 간주한다
  // (TLE/런타임에러 세부 구분은 DOM 샘플 확보 전까지 1차 범위에서 제외 - report05 참고).
  const scope = headingEl.closest('.console-content') || headingEl.parentElement;
  if (!scope) {
    return false;
  }
  const scopeText = scope.textContent || '';

  const totalMatch = scopeText.match(/합계\s*[:：]\s*([\d.]+)\s*\/\s*([\d.]+)/);
  if (totalMatch) {
    return Number.parseFloat(totalMatch[1]) >= Number.parseFloat(totalMatch[2]);
  }

  const runMatch = scopeText.match(/(\d+)\s*개\s*중\s*(\d+)\s*개\s*성공/);
  if (runMatch) {
    return Number.parseInt(runMatch[1], 10) === Number.parseInt(runMatch[2], 10);
  }

  return false;
}

function checkForNewGradingResults(container) {
  const headings = container.querySelectorAll('.console-heading');
  headings.forEach((headingEl) => {
    const headingText = headingEl.textContent;
    if (!isResultHeadingText(headingText)) {
      return;
    }
    if (processedGradingHeadings.has(headingEl)) {
      return;
    }
    processedGradingHeadings.add(headingEl);

    if (!isExtensionContextValid()) {
      handleContextInvalidated();
      return;
    }

    const passed = parseGradingPassed(headingEl);
    const source = getResultSource(headingText);
    console.log('[Cotea Content] 결과 감지:', source, passed ? '성공' : '실패');
    try {
      chrome.runtime.sendMessage({
        type: 'GRADING_RESULT',
        passed,
        source,
        problemId: parseProblemId(),
      });
    } catch (_error) {
      handleContextInvalidated();
    }
  });
}

function attachGradingObserver() {
  if (contextInvalidated) {
    return;
  }

  const container = findGradingContainer();
  if (!container || container === observedGradingContainerEl) {
    return;
  }

  observedGradingContainerEl = container;
  // 관찰 시작 시점에 이미 떠 있는 결과(예전 결과가 남아있는 새로고침 직후)는
  // "새로운 실행/채점"이 아니므로 baseline으로만 기록하고 알리지 않는다.
  container.querySelectorAll('.console-heading').forEach((headingEl) => {
    if (isResultHeadingText(headingEl.textContent)) {
      processedGradingHeadings.add(headingEl);
    }
  });

  if (gradingObserver) {
    gradingObserver.disconnect();
  }

  gradingObserver = new MutationObserver(() => {
    checkForNewGradingResults(container);
  });
  gradingObserver.observe(container, { childList: true, subtree: true });

  console.log('[Cotea Content] 채점 결과 DOM 관찰 시작');
}

attachObserverIntervalId = setInterval(() => {
  attachEditorObserver();
  attachGradingObserver();
}, 1000);

// background.js의 요청에 응답
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[Cotea Content] 메시지 수신:', message.type);

  if (message.type === 'GET_CODE') {
    // SPA 렌더 타이밍 이슈를 피하려고 짧게 재시도
    readCodeWithRetry(20, sendResponse);
    return true;
  }
});

function readCodeWithRetry(tryCount, sendResponse) {
  const code = getBestAvailableCode();
  const language = getCurrentLanguage();
  const problemId = parseProblemId();
  const problemTitle = getProblemTitle();

  if (isEditorPresent()) {
    console.log('[Cotea Content] 코드 전송:', code.length, '자,', language);
    sendResponse({ code, language, problemId, problemTitle });
    return;
  }

  if (tryCount <= 0) {
    console.log('[Cotea Content] 코드를 찾을 수 없음');
    sendResponse({ error: '코드 에디터를 찾을 수 없습니다.' });
    return;
  }

  setTimeout(() => {
    readCodeWithRetry(tryCount - 1, sendResponse);
  }, 150);
}

console.log('[Cotea Content] Content script 로드됨 - 프로그래머스 페이지:', window.location.href);
