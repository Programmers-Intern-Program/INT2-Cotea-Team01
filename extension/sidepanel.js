const DEFAULT_API_CONFIG = {
  mode: 'api',
  baseUrl: 'http://localhost:8080',
  endpoint: '/api/hint',
};

const DEFAULT_PROBLEM_ID = 1829;

const AVATAR_URL = chrome.runtime.getURL('mascot.png');

const STAGE_OPTIONS = [
  { value: 'BEFORE_SOLVE', label: '도전', colorClass: 'stage-before-solve' },
  { value: 'WRONG_ANSWER', label: '오답', colorClass: 'stage-wrong-answer' },
];

const STAGE_LABEL = Object.fromEntries(STAGE_OPTIONS.map((opt) => [opt.value, opt.label]));

const HINT_LEVEL_OPTIONS = [
  { hintLevel: 1, buttonId: 'hint_level_1', label: '1단계 관점 힌트', question: '이 문제를 어떤 관점에서 바라봐야 할지 모르겠어요' },
  { hintLevel: 2, buttonId: 'hint_level_2', label: '2단계 접근 힌트', question: '어떤 알고리즘으로 접근해야 할지 모르겠어요' },
  { hintLevel: 3, buttonId: 'hint_level_3', label: '3단계 구현 힌트', question: '구현 순서가 잘 안 잡혀요' },
  { hintLevel: 4, buttonId: 'hint_level_4', label: '4단계 코드 리뷰', question: '제 코드에서 문제가 있는지 봐주세요' },
];

const SUBMISSION_RESULT_OPTIONS = [
  { value: 'WRONG_ANSWER', label: '오답', whyButtonId: 'why_wrong', whyLabel: '왜 틀렸나요?', whyQuestion: '왜 틀렸는지 알려주세요' },
  { value: 'TIME_LIMIT_EXCEEDED', label: '시간초과', whyButtonId: 'why_tle', whyLabel: '왜 시간초과?', whyQuestion: '왜 시간초과가 났는지 알려주세요' },
  { value: 'RUNTIME_ERROR', label: '런타임 에러', whyButtonId: 'why_runtime_error', whyLabel: '왜 런타임 에러?', whyQuestion: '왜 런타임 에러가 났는지 알려주세요' },
];

const CATALOG_CHIPS = [
  ...HINT_LEVEL_OPTIONS.map((opt) => ({ label: opt.label, buttonId: opt.buttonId, question: opt.question, hintLevel: opt.hintLevel })),
  ...SUBMISSION_RESULT_OPTIONS.map((opt) => ({ label: opt.whyLabel, buttonId: opt.whyButtonId, question: opt.whyQuestion })),
];

const CHIP_BY_LABEL = Object.fromEntries(CATALOG_CHIPS.map((chip) => [chip.label, chip]));

const SAMPLE_CODE = `class Solution {\n    int answer = 0;\n\n    public int solution(int[] numbers,\n                        int target) {\n        dfs(numbers, target, 0, 0);\n        return answer;\n    }\n\n    void dfs(int[] numbers, int target,\n             int index, int current) {\n        if (index == numbers.length) {\n            if (current == target)\n                answer++;\n            return;\n        }\n        // ← 이 줄을 확인해 보세요!\n        dfs(numbers, target, index + 1,\n            current + numbers[index]);\n        dfs(numbers, target, index + 1,\n            current - numbers[index]);\n    }\n}`;

const state = {
  messages: [],
  input: '',
  activeChip: null,
  busy: false,
  latestCode: '',
  problemId: null,
  problemTitle: null,
  stage: null,
  hintLevel: null,
  submissionResult: null,
  apiConfig: { ...DEFAULT_API_CONFIG },
  syncing: false,
  codeDirty: false,
  onProgrammers: true,
  languageNotSupported: false,
  currentLanguage: 'Java',
  showLogin: false,
  loggedIn: false,
  kakaoNickname: null,
  authState: null,
  loginPending: false,
  loginSuccess: false,
  loginNotice: '',
  reportNotice: '',
};

const PROGRAMMERS_HOST = 'school.programmers.co.kr';
const OFF_SITE_PLACEHOLDER = '코티는 프로그래머스 코드 테스트에서만 작동합니다!';

const root = document.getElementById('root');

function nowLabel() {
  return new Date().toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function isComposerReady() {
  // 힌트 레벨/채점 결과는 세부 분류일 뿐이라, 상태(도전/오답)만 골랐으면
  // 자유 입력을 막지 않는다. 미선택 시 기본값 처리는 handleSend에서 한다.
  return Boolean(state.stage);
}

function pushStageDivider(label) {
  state.messages.push({ id: Date.now(), role: 'divider', text: label });
}

function isActiveChipUnedited() {
  if (!state.activeChip) {
    return false;
  }
  const chip = CHIP_BY_LABEL[state.activeChip];
  return Boolean(chip) && state.input === chip.question;
}

function buildConversationHistory() {
  return state.messages
    .filter((message) => message.role === 'user' || message.role === 'ai')
    .map((message) => ({
      role: message.role === 'ai' ? 'assistant' : 'user',
      text: message.text,
    }));
}

function buildHintRequest(questionText, chipLabel) {
  const chip = chipLabel ? CHIP_BY_LABEL[chipLabel] : null;
  const stage = state.stage;
  const problemId = state.problemId || DEFAULT_PROBLEM_ID;
  const base = {
    problemId,
    stage,
    userCode: state.latestCode || '',
    language: state.currentLanguage || 'Unknown',
    conversationHistory: buildConversationHistory(),
  };

  if (stage === 'WRONG_ANSWER') {
    base.submissionResult = state.submissionResult;
  }

  if (chip && chip.question === questionText) {
    return {
      ...base,
      questionType: 'BUTTON',
      buttonId: chip.buttonId,
      ...(chip.hintLevel ? { hintLevel: chip.hintLevel } : {}),
    };
  }

  return {
    ...base,
    questionType: 'FREE_TEXT',
    questionText,
    ...(stage === 'BEFORE_SOLVE' ? { hintLevel: state.hintLevel } : {}),
  };
}

function ensureWelcomeMessage() {
  if (state.messages.length > 0) {
    return;
  }

  const problemLabel = state.problemId ? `문제 #${state.problemId}` : '프로그래머스 문제';
  state.messages.push({
    id: Date.now(),
    role: 'ai',
    text: `안녕하세요! 저는 Cotea예요.\n\n${problemLabel}를 함께 풀어봐요. 먼저 아래에서 지금 상태를 선택해 주세요.`,
    timestamp: nowLabel(),
  });
}

async function syncPageContext() {
  try {
    const response = await sendRuntimeMessage({ type: 'SYNC_CODE' });
    if (response && response.error) {
      return;
    }
    if (response && typeof response.code === 'string') {
      state.latestCode = response.code;
    }
    if (response && response.problemId != null) {
      state.problemId = response.problemId;
    }
    if (response && response.problemTitle) {
      state.problemTitle = response.problemTitle;
    }
  } catch (_error) {
    // 프로그래머스 탭이 없으면 무시
  }
}

function sendRuntimeMessage(payload) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(payload, (response) => {
      const lastError = chrome.runtime.lastError;
      if (lastError) {
        reject(new Error(lastError.message));
        return;
      }
      resolve(response);
    });
  });
}

function applyAuthState(authState) {
  state.authState = authState || null;
  state.loggedIn = Boolean(authState && authState.accessToken);
  const user = authState && authState.user ? authState.user : null;
  state.kakaoNickname = user && user.nickname ? user.nickname : null;
}

function buildAvatarMarkup(glow, variant = 'chat') {
  return `
    <div class="cotea-avatar-shell cotea-avatar-shell--${variant} ${glow ? 'glow' : ''}">
      <img src="${AVATAR_URL}" alt="Cotea mascot" class="cotea-avatar-image">
    </div>
  `;
}

function tokenize(line) {
  const keywords = new Set(['public', 'private', 'class', 'static', 'void', 'int', 'long', 'boolean', 'String', 'return', 'if', 'else', 'new', 'null', 'true', 'false', 'this', 'super', 'import', 'package']);
  const out = [];
  let i = 0;

  while (i < line.length) {
    if (line[i] === '/' && line[i + 1] === '/') {
      out.push(`<span class="tk-comment">${escapeHtml(line.slice(i))}</span>`);
      break;
    }

    if (line[i] === '"') {
      let j = i + 1;
      while (j < line.length && line[j] !== '"') {
        j += 1;
      }
      out.push(`<span class="tk-string">${escapeHtml(line.slice(i, j + 1))}</span>`);
      i = j + 1;
      continue;
    }

    if (/\d/.test(line[i]) && (i === 0 || /\W/.test(line[i - 1]))) {
      let j = i;
      while (j < line.length && /\d/.test(line[j])) {
        j += 1;
      }
      out.push(`<span class="tk-number">${escapeHtml(line.slice(i, j))}</span>`);
      i = j;
      continue;
    }

    if (/[a-zA-Z_$]/.test(line[i])) {
      let j = i;
      while (j < line.length && /[\w$]/.test(line[j])) {
        j += 1;
      }
      const word = line.slice(i, j);
      if (keywords.has(word)) {
        out.push(`<span class="tk-keyword">${escapeHtml(word)}</span>`);
      } else if (/^[A-Z]/.test(word)) {
        out.push(`<span class="tk-type">${escapeHtml(word)}</span>`);
      } else if (j < line.length && line[j] === '(') {
        out.push(`<span class="tk-fn">${escapeHtml(word)}</span>`);
      } else {
        out.push(`<span class="tk-name">${escapeHtml(word)}</span>`);
      }
      i = j;
      continue;
    }

    out.push(`<span class="tk-plain">${escapeHtml(line[i])}</span>`);
    i += 1;
  }

  return out.join('');
}

function renderCodeBlock(code, highlightLine) {
  const lines = code.split('\n');
  return `
    <div class="java-block">
      <div class="java-titlebar">
        <div class="java-title-left">
          <div class="java-dots">
            <span class="java-dot red"></span>
            <span class="java-dot yellow"></span>
            <span class="java-dot green"></span>
          </div>
          <span class="java-filename">Solution.java</span>
        </div>
        <span class="java-badge">Java</span>
      </div>
      <div class="java-body">
        ${lines.map((line, index) => {
          const highlighted = highlightLine === index;
          return `
            <div class="java-line ${highlighted ? 'highlight' : ''}">
              <span class="java-line-no">${index + 1}</span>
              <span class="java-line-code">${tokenize(line) || '&nbsp;'}</span>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

function renderRichText(text) {
  return escapeHtml(text)
    .split('\n')
    .map((line) => {
      if (!line) {
        return '<div class="text-gap"></div>';
      }
      return `<p class="bubble-text-line">${line.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')}</p>`;
    })
    .join('');
}

function renderMessage(message) {
  if (message.role === 'divider') {
    return `
      <div class="stage-divider">
        <div class="stage-line"></div>
        <span class="stage-pill">${escapeHtml(message.text)}</span>
        <div class="stage-line"></div>
      </div>
    `;
  }

  const isAi = message.role === 'ai';
  const recommendationCards = Array.isArray(message.recommendations) && message.recommendations.length > 0
    ? renderRecommendationCards(message.recommendations)
    : '';
  const bubbleBody = message.glow
    ? '<div class="typing-row"><span class="dot"></span><span class="dot"></span><span class="dot"></span><span class="typing-label">분석 중이에요...</span></div>'
    : `${renderRichText(message.text)}${message.code ? renderCodeBlock(message.code.src, message.code.hl) : ''}${recommendationCards}`;

  return `
    <div class="message-row ${isAi ? 'ai' : 'user'}">
      ${isAi ? buildAvatarMarkup(Boolean(message.glow)) : ''}
      <div class="message-stack ${isAi ? 'left' : 'right'}">
        <div class="message-bubble ${isAi ? 'ai' : 'user'}">
          ${bubbleBody}
        </div>
        ${Array.isArray(message.trailChips) && message.trailChips.length > 0 ? `
          <div class="trail-chip-row">
            ${message.trailChips.map((label) => `<button type="button" class="trail-chip" data-trail-chip="${escapeHtml(label)}">${escapeHtml(label)}</button>`).join('')}
          </div>
        ` : ''}
        ${message.suggestConceptDrill && !message.glow ? `
          <div class="trail-chip-row">
            <button type="button" class="trail-chip concept-drill-chip" data-recommend-trigger>관련 유형 문제 추천</button>
          </div>
        ` : ''}
        ${message.timestamp && !message.glow ? `<span class="message-time">${escapeHtml(message.timestamp)}</span>` : ''}
      </div>
    </div>
  `;
}

function renderRecommendationCards(recommendations) {
  return `
    <div class="rec-list">
      ${recommendations.map((rec) => {
        const level = rec.level ? `<span class="rec-card-level">${escapeHtml(rec.level)}</span>` : '';
        return `
          <button type="button" class="rec-card" data-rec-url="${escapeHtml(rec.url || '')}" data-rec-problem="${escapeHtml(String(rec.problemId || ''))}">
            <span class="rec-card-head">${escapeHtml(rec.title || '문제')} ${level}</span>
            ${rec.reason ? `<span class="rec-card-reason">${escapeHtml(rec.reason)}</span>` : ''}
          </button>
        `;
      }).join('')}
    </div>
  `;
}

function renderBusyIndicator() {
  if (!state.busy) {
    return '';
  }

  return `
    <div class="message-row ai">
      ${buildAvatarMarkup(false)}
      <div class="message-stack left">
        <div class="message-bubble ai busy-bubble">
          <div class="typing-only"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
        </div>
      </div>
    </div>
  `;
}

function renderHeaderTitle() {
  if (state.problemTitle) {
    return `프로그래머스 문제 - ${state.problemTitle}`;
  }
  if (state.problemId) {
    return `문제 #${state.problemId}`;
  }
  return '프로그래머스 문제';
}

function renderSyncLabel() {
  if (!state.latestCode) {
    return '코드 동기화 대기 중';
  }
  if (state.codeDirty) {
    return '코드를 반영하려면 동기화 버튼을 눌러주세요!';
  }
  return '최신 코드 동기화 완료';
}

function renderSyncDotClass() {
  if (!state.latestCode) {
    return '';
  }
  return state.codeDirty ? 'dirty' : 'ok';
}

function renderComposerPlaceholder() {
  if (!state.onProgrammers) {
    return OFF_SITE_PLACEHOLDER;
  }
  if (!state.stage) {
    return '먼저 위에서 지금 상태를 선택해주세요';
  }
  return 'Cotea에게 질문하세요...';
}

function renderStageSelector() {
  return `
    <div class="stage-select-row">
      ${STAGE_OPTIONS.map((opt) => {
        const active = state.stage === opt.value;
        return `<button type="button" class="stage-chip ${active ? `active ${opt.colorClass}` : ''}" data-stage="${opt.value}" ${!state.onProgrammers || state.busy ? 'disabled' : ''}>${escapeHtml(opt.label)}</button>`;
      }).join('')}
    </div>
  `;
}

function renderHintLevelSelector() {
  if (state.stage !== 'BEFORE_SOLVE') {
    return '';
  }
  return `
    <div class="sub-select-row hint-level-grid">
      ${HINT_LEVEL_OPTIONS.map((opt) => {
        const active = state.hintLevel === opt.hintLevel;
        return `<button type="button" class="sub-chip ${active ? 'active' : ''}" data-hint-level="${opt.hintLevel}" ${!state.onProgrammers || state.busy ? 'disabled' : ''}>${escapeHtml(opt.label)}</button>`;
      }).join('')}
    </div>
  `;
}

function renderSubmissionResultSelector() {
  if (state.stage !== 'WRONG_ANSWER') {
    return '';
  }
  return `
    <div class="sub-select-row">
      ${SUBMISSION_RESULT_OPTIONS.map((opt) => {
        const active = state.submissionResult === opt.value;
        return `<button type="button" class="sub-chip ${active ? 'active' : ''}" data-submission-result="${opt.value}" ${!state.onProgrammers || state.busy ? 'disabled' : ''}>${escapeHtml(opt.label)}</button>`;
      }).join('')}
    </div>
  `;
}

function avatarInitial() {
  return (state.kakaoNickname || '').trim().charAt(0) || '나';
}

function renderAccountButtonInner() {
  if (state.loggedIn) {
    return `<span class="account-avatar">${escapeHtml(avatarInitial())}</span>`;
  }
  return `
    <svg class="account-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
      <circle cx="12" cy="7" r="4"></circle>
    </svg>
  `;
}

function renderProfileView() {
  return `
    <section class="login-view">
      <div class="login-card">
        <div class="login-card-head">
          <p class="login-title">내 계정</p>
          <button type="button" id="login-close" class="login-close" aria-label="닫기">✕</button>
        </div>
        <div class="profile-row">
          <span class="account-avatar account-avatar--lg">${escapeHtml(avatarInitial())}</span>
          <div class="profile-info">
            <p class="profile-name">${escapeHtml(state.kakaoNickname || '')}</p>
            <p class="profile-sub">카카오 계정으로 로그인됨</p>
          </div>
        </div>
        <button type="button" id="report-button" class="report-button">리포트 보기</button>
        ${state.reportNotice ? `<p class="login-notice">${escapeHtml(state.reportNotice)}</p>` : ''}
        <button type="button" id="logout-button" class="logout-button">로그아웃</button>
      </div>
    </section>
  `;
}

function renderLoginFormView() {
  return `
    <section class="login-view">
      <div class="login-card">
        <div class="login-card-head">
          <p class="login-title">로그인</p>
          <button type="button" id="login-close" class="login-close" aria-label="닫기">✕</button>
        </div>
        <p class="login-desc">로그인하면 학습 리포트 등 추가 기능을 이용할 수 있어요. 로그인하지 않아도 힌트 요청은 그대로 사용할 수 있습니다.</p>
        ${state.loginNotice ? `<p class="login-notice ${state.loginSuccess ? 'success' : ''}">${escapeHtml(state.loginNotice)}</p>` : ''}
        <button type="button" id="kakao-login-button" class="kakao-login-button" ${state.loginPending || state.loginSuccess ? 'disabled' : ''}>
          <span class="kakao-bubble-icon" aria-hidden="true"></span>
          ${state.loginPending ? '확인 중...' : '카카오로 시작하기'}
        </button>
      </div>
    </section>
  `;
}

function renderLoginView() {
  // loginSuccess는 "방금 로그인 성공" 전환 구간 — 이 동안은 로그인됐어도 웰컴 문구가 있는
  // 로그인 카드를 유지하고, 구간이 끝나면(auto-close) 다음에 열 때부터 프로필 카드로 전환된다.
  if (state.loggedIn && !state.loginSuccess) {
    return renderProfileView();
  }
  return renderLoginFormView();
}

const COMPOSER_MAX_HEIGHT = 120;

function autoResizeComposerInput() {
  const textarea = document.getElementById('question-input');
  if (!textarea) {
    return;
  }
  textarea.style.height = 'auto';
  const nextHeight = Math.min(textarea.scrollHeight, COMPOSER_MAX_HEIGHT);
  textarea.style.height = `${nextHeight}px`;
  textarea.style.overflowY = textarea.scrollHeight > COMPOSER_MAX_HEIGHT ? 'auto' : 'hidden';
}

const FOCUS_TRACKED_INPUT_IDS = ['question-input'];

function renderShell() {
  const focusedId = document.activeElement && document.activeElement.id;
  const focusedSelection = FOCUS_TRACKED_INPUT_IDS.includes(focusedId)
    ? [document.activeElement.selectionStart, document.activeElement.selectionEnd]
    : null;

  root.innerHTML = `
    <div class="cotea-stage">
      <div class="cotea-frame">
        <header class="cotea-header">
          <div class="cotea-header-inner">
            <div class="cotea-header-badge">${buildAvatarMarkup(false, 'header')}</div>
            <div class="cotea-header-copy">
              <p class="cotea-kicker">Cotea AI Tutor</p>
              <p class="cotea-title">${escapeHtml(renderHeaderTitle())}</p>
            </div>
            <button type="button" id="account-button" class="header-action account-button ${state.showLogin ? 'active' : ''}" aria-label="${state.loggedIn ? escapeHtml(state.kakaoNickname || '내 계정') : '로그인'}" data-tooltip="${state.loggedIn ? escapeHtml(state.kakaoNickname || '내 계정') : '로그인'}">
              ${renderAccountButtonInner()}
            </button>
            <button type="button" id="sync-button" class="header-action sync-button ${state.syncing ? 'syncing' : ''}" aria-label="코드 동기화" data-tooltip="코드 동기화" ${state.syncing || !state.onProgrammers ? 'disabled' : ''}>
              <svg class="sync-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="23 4 23 10 17 10"></polyline>
                <polyline points="1 20 1 14 7 14"></polyline>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
              </svg>
            </button>
          </div>
        </header>

        ${state.showLogin ? renderLoginView() : `
        <section class="cotea-chat-scroll" id="chat-scroll">
          ${state.messages.map(renderMessage).join('')}
          ${renderBusyIndicator()}
        </section>

        <div class="cotea-bottom-shell">
          ${renderStageSelector()}
          ${renderHintLevelSelector()}
          ${renderSubmissionResultSelector()}

          <div class="sync-row">
            <span class="sync-dot ${renderSyncDotClass()}"></span>
            <span class="sync-label ${state.codeDirty ? 'dirty' : ''}">${escapeHtml(renderSyncLabel())}</span>
          </div>

          <div class="composer-row ${isActiveChipUnedited() ? 'caret-mode' : ''}">
            <div class="composer-input-wrap">
              <textarea id="question-input" rows="1" placeholder="${escapeHtml(renderComposerPlaceholder())}" ${state.busy || !state.onProgrammers || !isComposerReady() ? 'disabled' : ''}>${escapeHtml(state.input)}</textarea>
              ${isActiveChipUnedited() ? `<div class="fake-caret-layer"><span class="ghost-text">${escapeHtml(state.input)}</span><span class="fake-caret"></span></div>` : ''}
            </div>
            <button type="button" id="send-button" class="send-button" ${!state.input.trim() || state.busy || !state.onProgrammers || !isComposerReady() ? 'disabled' : ''}>
              <span class="send-arrow">↗</span>
            </button>
          </div>

          <div class="composer-help">
            <span class="return-icon">↵</span>
            <p>Enter로 전송 · Cotea는 실수할 수 있어요</p>
          </div>
        </div>
        `}
      </div>
    </div>
  `;

  bindEvents();
  autoResizeComposerInput();

  if (focusedId) {
    const elementToFocus = document.getElementById(focusedId);
    if (elementToFocus) {
      elementToFocus.focus();
      if (focusedSelection && typeof elementToFocus.setSelectionRange === 'function') {
        elementToFocus.setSelectionRange(focusedSelection[0], focusedSelection[1]);
      }
    }
  }

  const chatScroll = document.getElementById('chat-scroll');
  if (chatScroll) {
    chatScroll.scrollTop = chatScroll.scrollHeight;
  }
}

function bindEvents() {
  const accountButton = document.getElementById('account-button');
  if (accountButton) {
    accountButton.addEventListener('click', () => {
      state.showLogin = !state.showLogin;
      if (!state.showLogin) {
        state.loginNotice = '';
        state.reportNotice = '';
      }
      renderShell();
    });
  }

  const loginClose = document.getElementById('login-close');
  if (loginClose) {
    loginClose.addEventListener('click', () => {
      state.showLogin = false;
      state.loginNotice = '';
      state.reportNotice = '';
      renderShell();
    });
  }

  const kakaoLoginButton = document.getElementById('kakao-login-button');
  if (kakaoLoginButton) {
    kakaoLoginButton.addEventListener('click', handleKakaoLogin);
  }

  const reportButton = document.getElementById('report-button');
  if (reportButton) {
    reportButton.addEventListener('click', () => {
      state.reportNotice = '아직 준비중입니다';
      renderShell();
    });
  }

  const logoutButton = document.getElementById('logout-button');
  if (logoutButton) {
    logoutButton.addEventListener('click', handleLogout);
  }

  const syncButton = document.getElementById('sync-button');
  if (syncButton) {
    syncButton.addEventListener('click', handleSync);
  }

  const questionInput = document.getElementById('question-input');
  if (questionInput) {
    questionInput.addEventListener('input', (event) => {
      state.input = event.target.value;
      if (!isActiveChipUnedited()) {
        state.activeChip = null;
      }
      if (event.isComposing) {
        // 한글 등 IME 조합 중에는 DOM을 재생성하면 조합이 깨지므로 렌더링을 건너뜀
        return;
      }
      renderShell();
    });

    questionInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.isComposing) {
        event.preventDefault();
        handleSend();
      }
    });
  }

  const sendButton = document.getElementById('send-button');
  if (sendButton) {
    sendButton.addEventListener('click', handleSend);
  }

  document.querySelectorAll('[data-trail-chip]').forEach((button) => {
    button.addEventListener('click', () => {
      const label = button.dataset.trailChip || '';
      state.input = label;
      state.activeChip = null;
      renderShell();
    });
  });

  document.querySelectorAll('[data-stage]').forEach((button) => {
    button.addEventListener('click', () => {
      handleStageSelect(button.dataset.stage || '');
    });
  });

  document.querySelectorAll('[data-hint-level]').forEach((button) => {
    button.addEventListener('click', () => {
      handleHintLevelSelect(Number(button.dataset.hintLevel));
    });
  });

  document.querySelectorAll('[data-submission-result]').forEach((button) => {
    button.addEventListener('click', () => {
      handleSubmissionResultSelect(button.dataset.submissionResult || '');
    });
  });

  document.querySelectorAll('[data-recommend-trigger]').forEach((button) => {
    button.addEventListener('click', fetchRecommendations);
  });

  document.querySelectorAll('[data-rec-url]').forEach((button) => {
    button.addEventListener('click', () => {
      openProblemUrl(button.dataset.recUrl || '');
    });
  });
}

function handleStageSelect(value) {
  if (state.busy) {
    return;
  }
  if (state.stage === value) {
    return;
  }
  state.stage = value;
  state.hintLevel = null;
  state.submissionResult = null;
  state.activeChip = null;
  state.input = '';
  pushStageDivider(STAGE_LABEL[value] || value);
  renderShell();
}

function applyGradingResult(gradingResult) {
  // 1차 범위: 합/불만 자동 감지하고, TLE/런타임에러 세부 구분은 하지 않은 채
  // 모든 불합격을 기본값 오답(WRONG_ANSWER)으로 취급한다 (report05 논의 참고).
  // 이미 사용자가 시간초과/런타임에러로 직접 고쳐놓은 값은 재감지 시에도 덮어쓰지 않는다.
  if (!gradingResult || gradingResult.passed !== false) {
    return;
  }
  // problemId가 없거나(파싱 실패) 지금 보고 있는 문제와 다르면 무시한다.
  // 다른 탭에서 감지된 결과가 지금 패널에 잘못 반영되는 것을 막기 위함이라,
  // 호출부(초기 로딩/실시간 반영) 둘 다 이 한 곳만 거치면 되도록 여기서 검사한다.
  if (gradingResult.problemId == null || gradingResult.problemId !== state.problemId) {
    return;
  }

  const alreadyInWrongAnswerFlow = state.stage === 'WRONG_ANSWER';
  state.stage = 'WRONG_ANSWER';
  state.hintLevel = null;
  state.activeChip = null;
  if (!state.submissionResult) {
    state.submissionResult = 'WRONG_ANSWER';
  }
  const sourceLabel = gradingResult.source === 'run' ? '코드 실행' : '채점 결과';
  pushStageDivider(alreadyInWrongAnswerFlow ? `${sourceLabel} 자동 감지: 다시 실패했어요` : `${sourceLabel} 자동 감지: 오답이에요`);
}

function handleHintLevelSelect(level) {
  if (state.busy) {
    return;
  }
  const opt = HINT_LEVEL_OPTIONS.find((o) => o.hintLevel === level);
  if (!opt) {
    return;
  }
  state.hintLevel = level;
  state.input = opt.question;
  state.activeChip = opt.label;
  renderShell();
  const input = document.getElementById('question-input');
  if (input) {
    input.focus();
  }
}

function handleSubmissionResultSelect(value) {
  if (state.busy) {
    return;
  }
  const opt = SUBMISSION_RESULT_OPTIONS.find((o) => o.value === value);
  if (!opt) {
    return;
  }
  state.submissionResult = value;
  state.input = opt.whyQuestion;
  state.activeChip = opt.whyLabel;
  renderShell();
  const input = document.getElementById('question-input');
  if (input) {
    input.focus();
  }
}

async function handleKakaoLogin() {
  if (state.loginPending) {
    return;
  }
  state.loginPending = true;
  state.loginSuccess = false;
  state.loginNotice = '';
  renderShell();

  try {
    const response = await sendRuntimeMessage({ type: 'LOGIN_KAKAO' });
    if (!response || !response.ok) {
      throw new Error((response && response.error) || '카카오 로그인에 실패했습니다.');
    }
    applyAuthState(response.authState);
    state.loginPending = false;
    state.loginSuccess = true;
    state.loginNotice = `${state.kakaoNickname || '카카오 사용자'}님 환영합니다!`;
    renderShell();

    setTimeout(() => {
      state.showLogin = false;
      state.loginNotice = '';
      state.loginSuccess = false;
      renderShell();
    }, 900);
  } catch (error) {
    state.loginPending = false;
    state.loginSuccess = false;
    state.loginNotice = error.message;
    renderShell();
  }
}

async function handleLogout() {
  try {
    await sendRuntimeMessage({ type: 'LOGOUT' });
  } catch (_error) {
    // LOGOUT 메시지 전송이 실패해도 로컬 storage는 반드시 정리한다
  }
  try {
    await chrome.storage.local.set({ authState: null });
  } catch (_error) {
    // ignore
  }
  applyAuthState(null);
  state.showLogin = false;
  state.reportNotice = '';
  renderShell();
}

async function handleSync() {
  if (state.syncing || !state.onProgrammers) {
    return;
  }

  state.syncing = true;
  renderShell();

  try {
    const response = await sendRuntimeMessage({ type: 'SYNC_CODE' });

    if (response && response.error) {
      state.messages.push({
        id: Date.now(),
        role: 'ai',
        text: response.error,
        timestamp: nowLabel(),
      });
    } else if (response && response.code) {
      state.latestCode = response.code;
      state.codeDirty = false;
      if (response.problemId != null) {
        state.problemId = response.problemId;
      }
      if (response.problemTitle) {
        state.problemTitle = response.problemTitle;
      }
      if (response.warning) {
        state.messages.push({
          id: Date.now(),
          role: 'ai',
          text: response.warning,
          timestamp: nowLabel(),
        });
      }
    }
  } catch (error) {
    console.error('[Cotea] 코드 동기화 실패:', error);
    state.messages.push({
      id: Date.now(),
      role: 'ai',
      text: '코드 동기화 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
      timestamp: nowLabel(),
    });
  } finally {
    state.syncing = false;
    renderShell();
  }
}

async function dispatchHintRequest(hintRequest, displayText) {
  state.messages.push({
    id: Date.now(),
    role: 'user',
    text: displayText,
    timestamp: nowLabel(),
  });
  state.busy = true;
  renderShell();

  try {
    const response = await sendRuntimeMessage({
      type: 'ASK_AI',
      hintRequest,
    });

    state.messages.push({
      id: Date.now() + 1,
      role: 'ai',
      text: response && response.answer ? response.answer : '응답을 받지 못했습니다.',
      suggestConceptDrill: Boolean(response && response.suggestConceptDrill),
      timestamp: nowLabel(),
    });
  } catch (error) {
    console.error('[Cotea] 힌트 요청 실패:', error);
    state.messages.push({
      id: Date.now() + 2,
      role: 'ai',
      text: '오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
      timestamp: nowLabel(),
    });
  } finally {
    state.busy = false;
    renderShell();
  }
}

function openProblemUrl(url) {
  if (!url) {
    return;
  }
  if (typeof chrome !== 'undefined' && chrome.tabs && typeof chrome.tabs.create === 'function') {
    chrome.tabs.create({ url });
  } else {
    window.open(url, '_blank');
  }
}

async function fetchRecommendations() {
  if (state.busy || !state.onProgrammers) {
    return;
  }

  const problemId = state.problemId || DEFAULT_PROBLEM_ID;
  const baseUrl = ((state.apiConfig && state.apiConfig.baseUrl) || DEFAULT_API_CONFIG.baseUrl).replace(/\/$/, '');
  state.busy = true;
  renderShell();

  try {
    const response = await fetch(`${baseUrl}/api/recommend?problemId=${encodeURIComponent(problemId)}&limit=3`, {
      method: 'GET',
    });

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
      throw new Error(`추천 요청 실패: ${response.status}${detail}`);
    }

    const data = await response.json();
    const recommendations = Array.isArray(data.recommendations) ? data.recommendations : [];
    if (recommendations.length === 0) {
      state.messages.push({
        id: Date.now(),
        role: 'ai',
        text: '지금은 추천할 만한 비슷한 유형의 문제를 찾지 못했어요.',
        timestamp: nowLabel(),
      });
    } else {
      state.messages.push({
        id: Date.now(),
        role: 'ai',
        text: '이 유형을 먼저 가볍게 연습해볼 문제예요. 눌러서 풀어본 뒤 원래 문제로 돌아와요!',
        recommendations,
        timestamp: nowLabel(),
      });
    }
  } catch (error) {
    console.error('[Cotea] 추천 요청 실패:', error);
    state.messages.push({
      id: Date.now(),
      role: 'ai',
      text: '추천 요청 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.',
      timestamp: nowLabel(),
    });
  } finally {
    state.busy = false;
    renderShell();
  }
}

async function handleSend() {
  const question = state.input.trim();
  const chipLabel = state.activeChip;
  if (!question || state.busy || !state.onProgrammers || !isComposerReady()) {
    return;
  }

  state.busy = true;
  renderShell();
  // 질문을 보내기 직전에 항상 최신 코드로 재동기화한다 (수동 동기화 버튼에만
  // 의존하면 사용자가 깜빡했을 때 낡은 코드가 힌트 API로 전송될 수 있음)
  await syncPageContext();

  const hintRequest = buildHintRequest(question, chipLabel);
  state.input = '';
  state.activeChip = null;
  await dispatchHintRequest(hintRequest, question);
}

function refreshActiveTabStatus() {
  if (typeof chrome === 'undefined' || !chrome.tabs || typeof chrome.tabs.query !== 'function') {
    return;
  }

  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    const activeTab = tabs && tabs[0];
    const onProgrammers = Boolean(activeTab && activeTab.url && activeTab.url.includes(PROGRAMMERS_HOST));

    if (onProgrammers !== state.onProgrammers) {
      state.onProgrammers = onProgrammers;
      renderShell();
    }
  });
}

async function initialize() {
  let pendingGradingResult = null;
  try {
    const response = await sendRuntimeMessage({ type: 'GET_PANEL_STATE' });
    state.latestCode = response && response.latestCode ? response.latestCode : '';
    state.problemId = response && response.problemId != null ? response.problemId : null;
    state.problemTitle = response && response.problemTitle ? response.problemTitle : null;
    state.apiConfig = { ...DEFAULT_API_CONFIG, ...((response && response.apiConfig) || {}) };
    applyAuthState(response && response.authState ? response.authState : null);
    state.codeDirty = Boolean(response && response.codeDirty);
    state.languageNotSupported = Boolean(response && response.languageNotSupported);
    state.currentLanguage = (response && response.currentLanguage) || 'Java';

    // 패널을 열기 전에 이미 코드 실행/채점을 해서 저장돼있던 결과가 있으면
    // 지금 막 감지된 것처럼 반영한다. 문제 ID 일치 여부는 applyGradingResult가 검사한다.
    if (response && response.gradingResult) {
      pendingGradingResult = response.gradingResult;
    }
  } catch (error) {
    console.error('[Cotea] 초기 상태 조회 실패:', error);
    state.messages.push({
      id: Date.now() + 3,
      role: 'ai',
      text: '초기 상태를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.',
      timestamp: nowLabel(),
    });
  }

  await syncPageContext();
  ensureWelcomeMessage();
  if (pendingGradingResult) {
    applyGradingResult(pendingGradingResult);
  }
  refreshActiveTabStatus();

  if (typeof chrome !== 'undefined' && chrome.tabs) {
    if (chrome.tabs.onActivated) {
      chrome.tabs.onActivated.addListener(refreshActiveTabStatus);
    }
    if (chrome.tabs.onUpdated) {
      chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
        if (changeInfo.status === 'complete' || changeInfo.url) {
          refreshActiveTabStatus();
        }
      });
    }
  }

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName !== 'local') {
      return;
    }

    if (changes.latestCode) {
      state.latestCode = changes.latestCode.newValue || '';
    }

    if (changes.problemId) {
      state.problemId = changes.problemId.newValue ?? null;
    }

    if (changes.problemTitle) {
      state.problemTitle = changes.problemTitle.newValue || null;
    }

    if (changes.apiConfig) {
      state.apiConfig = { ...DEFAULT_API_CONFIG, ...(changes.apiConfig.newValue || {}) };
    }

    if (changes.authState) {
      applyAuthState(changes.authState.newValue || null);
    }

    if (changes.codeDirty) {
      state.codeDirty = Boolean(changes.codeDirty.newValue);
    }

    if (changes.currentLanguage) {
      state.currentLanguage = changes.currentLanguage.newValue || 'Java';
    }

    if (changes.languageNotSupported) {
      const nextLanguageNotSupported = Boolean(changes.languageNotSupported.newValue);
      if (nextLanguageNotSupported && !state.languageNotSupported) {
        state.messages.push({
          id: Date.now(),
          role: 'ai',
          text: `현재 선택 언어(${state.currentLanguage})는 미지원입니다. Java로 바꿔주세요.`,
          timestamp: nowLabel(),
        });
      }
      state.languageNotSupported = nextLanguageNotSupported;
    }

    if (changes.gradingResult) {
      applyGradingResult(changes.gradingResult.newValue);
    }

    renderShell();
  });

  renderShell();
}

initialize();
