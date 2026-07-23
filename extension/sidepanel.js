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

// Lucide 아이콘과 동일한 24x24 stroke 스타일로 직접 작성한 인라인 SVG.
// (이 프로젝트엔 React/번들러가 없어 lucide-react를 쓸 수 없고, 계정/동기화
// 버튼 아이콘과 같은 방식으로 순수 SVG 문자열을 사용한다.)
const HINT_ICON_SVG = {
  eye: '<svg class="hint-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"></path><circle cx="12" cy="12" r="3"></circle></svg>',
  target: '<svg class="hint-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle></svg>',
  wrench: '<svg class="hint-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94z"></path></svg>',
  fileText: '<svg class="hint-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"></path><path d="M14 2v4a2 2 0 0 0 2 2h4"></path><path d="M10 9H8"></path><path d="M16 13H8"></path><path d="M16 17H8"></path></svg>',
};

const HINT_LEVEL_OPTIONS = [
  { hintLevel: 1, buttonId: 'hint_level_1', label: '관점 힌트', icon: HINT_ICON_SVG.eye, question: '이 문제를 어떤 관점에서 바라봐야 할지 모르겠어요' },
  { hintLevel: 2, buttonId: 'hint_level_2', label: '접근 힌트', icon: HINT_ICON_SVG.target, question: '어떤 알고리즘으로 접근해야 할지 모르겠어요' },
  { hintLevel: 3, buttonId: 'hint_level_3', label: '구현 힌트', icon: HINT_ICON_SVG.wrench, question: '구현 순서가 잘 안 잡혀요' },
  { hintLevel: 4, buttonId: 'hint_level_4', label: '코드 리뷰', icon: HINT_ICON_SVG.fileText, question: '제 코드에서 문제가 있는지 봐주세요' },
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
  lastMessageStage: null,
  // 대화가 진행 중인 상태에서 다른 문제로 이동한 걸 감지했을 때, 사용자가
  // "이전 대화 유지"/"새로 시작"을 고르기 전까지 여기에 새 문제 id를 잠깐 들고 있는다.
  pendingProblemSwitch: null,
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

  // 문제 번호를 언급하면, 대화 도중 실시간으로 다른 문제로 넘어가도 웰컴
  // 메시지는 그대로 남아있어서 예전 문제 번호와 안 맞는 문구가 돼버린다.
  // 그래서 특정 문제를 지칭하지 않는 문구로 고정한다.
  state.messages.push({
    id: Date.now(),
    role: 'ai',
    text: '안녕하세요! 저는 Cotea예요.\n\n문제를 함께 풀어봐요. 먼저 아래에서 지금 상태를 선택해 주세요.\n아직 문제를 푸는 중이라면 "도전"을, 제출했지만 틀렸다면 "오답"을 눌러주세요.',
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
  // 다른 사이트에 있을 땐 problemId/problemTitle이 storage에 남아있어도
  // 마지막으로 봤던 문제 정보를 보여주면 안 된다 (프로그래머스를 보고 있는
  // 것처럼 착각하게 됨).
  if (!state.onProgrammers) {
    return '프로그래머스 문제';
  }
  if (state.problemId && state.problemTitle) {
    return `문제 #${state.problemId}: ${state.problemTitle}`;
  }
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
        return `<button type="button" class="sub-chip ${active ? 'active' : ''}" data-hint-level="${opt.hintLevel}" ${!state.onProgrammers || state.busy ? 'disabled' : ''}>${opt.icon}<span>${escapeHtml(opt.label)}</span></button>`;
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

function renderProblemSwitchView() {
  const newProblemId = state.pendingProblemSwitch.problemId;
  return `
    <section class="login-view">
      <div class="login-card">
        <p class="login-title">다른 문제로 이동했어요</p>
        <p class="login-desc">문제 #${escapeHtml(String(newProblemId))}(으)로 이동한 것 같아요. 지금까지의 대화를 이어서 쓸까요, 새로 시작할까요?</p>
        <div class="problem-switch-actions">
          <button type="button" id="problem-switch-keep" class="problem-switch-button keep">이전 대화 유지</button>
          <button type="button" id="problem-switch-reset" class="problem-switch-button reset">새로 시작</button>
        </div>
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
            <button type="button" id="sync-button" class="header-action sync-button ${state.syncing ? 'syncing' : ''}" aria-label="코드 동기화" data-tooltip="코드 동기화" ${state.syncing || !state.onProgrammers || state.pendingProblemSwitch ? 'disabled' : ''}>
              <svg class="sync-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="23 4 23 10 17 10"></polyline>
                <polyline points="1 20 1 14 7 14"></polyline>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
              </svg>
            </button>
          </div>
        </header>

        ${state.showLogin ? renderLoginView() : state.pendingProblemSwitch ? renderProblemSwitchView() : `
        <section class="cotea-chat-scroll" id="chat-scroll">
          ${state.messages.map(renderMessage).join('')}
          ${renderBusyIndicator()}
        </section>

        <div class="cotea-bottom-shell">
          <div class="stage-row-wrap">
            ${renderStageSelector()}
            <div class="sync-row">
              <span class="sync-dot ${renderSyncDotClass()}"></span>
              <span class="sync-label ${state.codeDirty ? 'dirty' : ''}">${escapeHtml(renderSyncLabel())}</span>
            </div>
          </div>
          ${renderHintLevelSelector()}
          ${renderSubmissionResultSelector()}

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

  const problemSwitchKeepButton = document.getElementById('problem-switch-keep');
  if (problemSwitchKeepButton) {
    problemSwitchKeepButton.addEventListener('click', handleKeepConversationOnProblemSwitch);
  }

  const problemSwitchResetButton = document.getElementById('problem-switch-reset');
  if (problemSwitchResetButton) {
    problemSwitchResetButton.addEventListener('click', handleStartFreshOnProblemSwitch);
  }

  const reportButton = document.getElementById('report-button');
  if (reportButton) {
    reportButton.addEventListener('click', handleWeeklyReport);
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
  // 버튼 클릭 시점엔 구분선을 남기지 않는다 - 실제로 질문을 보낼 때
  // handleSend에서 그 시점의 상태를 기준으로 필요하면 남긴다.
  state.stage = value;
  state.hintLevel = null;
  state.submissionResult = null;
  state.activeChip = null;
  state.input = '';
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
  // 지금 실제로 프로그래머스 문제 페이지를 보고 있을 때만 반영한다. problemId가
  // 우연히 일치하더라도, 패널이 다른 사이트/문제 목록 페이지를 보고 있는 동안
  // (onProgrammers=false) 실시간 채점 결과(chrome.storage.onChanged)가 와서
  // 상태를 바꿔버리는 걸 막기 위함. problemId 검사와 마찬가지로 호출부
  // (초기 로딩/실시간 반영) 둘 다 이 한 곳만 거치면 되도록 여기서 검사한다.
  if (!state.onProgrammers) {
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
  // 여기서 이미 구분선을 남겼으니, 바로 이어서 질문을 보내도 handleSend가
  // 상태 변화로 착각해 중복 구분선을 또 남기지 않도록 동기화해둔다.
  state.lastMessageStage = 'WRONG_ANSWER';
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

async function handleWeeklyReport() {
  state.reportNotice = '최근 7일 리포트를 불러오는 중입니다...';
  renderShell();

  try {
    const response = await sendRuntimeMessage({ type: 'GET_WEEKLY_REPORT' });
    if (!response || !response.ok) {
      throw new Error((response && response.error) || '리포트를 불러오지 못했습니다.');
    }
    state.reportNotice = formatWeeklyReport(response.report);
  } catch (error) {
    state.reportNotice = error.message;
  }
  renderShell();
}

function formatWeeklyReport(report) {
  if (!report || !report.totalHintCount) {
    return '최근 7일 동안 저장된 힌트 요청이 없습니다.';
  }

  const topWeakness = report.topWeaknessTypes && report.topWeaknessTypes[0]
    ? `${report.topWeaknessTypes[0].name} ${report.topWeaknessTypes[0].count}회`
    : '약점 데이터 없음';
  const topTag = report.topTags && report.topTags[0]
    ? `${report.topTags[0].name} ${report.topTags[0].count}회`
    : '태그 데이터 없음';

  return `최근 ${report.periodDays}일 힌트 ${report.totalHintCount}회 · 주요 약점: ${topWeakness} · 자주 막힌 태그: ${topTag}`;
}

async function handleSync() {
  if (state.syncing || !state.onProgrammers || state.pendingProblemSwitch) {
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
    const headers = {};
    if (state.authState && state.authState.accessToken) {
      headers.Authorization = `${state.authState.tokenType || 'Bearer'} ${state.authState.accessToken}`;
    }
    const response = await fetch(`${baseUrl}/api/recommend?problemId=${encodeURIComponent(problemId)}&limit=3`, {
      method: 'GET',
      headers,
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

  // 오답 상태에서 채점 결과를 안 골라도 자유 입력이 가능해야 하는데,
  // 백엔드는 WRONG_ANSWER 단계에서 submissionResult를 필수로 요구한다
  // (HintRequestValidator.validateSubmissionResult). 안 골랐으면 기본값으로 채운다.
  if (state.stage === 'WRONG_ANSWER' && !state.submissionResult) {
    state.submissionResult = 'WRONG_ANSWER';
  }

  // 상태를 바꿀 때마다가 아니라, 실제로 질문을 보낼 때 그 시점의 상태가
  // 직전 질문 때와 달라졌으면 그때만 구분선을 남긴다.
  if (state.stage !== state.lastMessageStage) {
    pushStageDivider(STAGE_LABEL[state.stage] || state.stage);
    state.lastMessageStage = state.stage;
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

function parseProblemIdFromUrl(url) {
  if (!url) {
    return null;
  }
  // content.js의 parseProblemId()와 동일한 패턴 - 문제 상세 페이지인지 판별용
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

function isTabOnProgrammers(tab) {
  // 도메인만 확인하면 문제 목록 페이지(코드 에디터가 없는 페이지)도 true가 되어
  // storage에 남아있는 예전 문제 번호가 그대로 노출된다. 실제 문제 상세 페이지인
  // 경우에만 true로 취급한다.
  return Boolean(tab && tab.url && tab.url.includes(PROGRAMMERS_HOST) && parseProblemIdFromUrl(tab.url) != null);
}

function queryActiveTab() {
  return new Promise((resolve) => {
    if (typeof chrome === 'undefined' || !chrome.tabs || typeof chrome.tabs.query !== 'function') {
      resolve(null);
      return;
    }
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      resolve(tabs && tabs[0] ? tabs[0] : null);
    });
  });
}

function hasActiveConversation() {
  // 웰컴 메시지 하나만 있는 상태는 "아직 진행 중인 대화"로 치지 않는다 -
  // 사용자가 실제로 입력을 하거나 상태를 고른 적이 있어야 잃을 게 있는 것.
  return state.stage != null || state.messages.some((message) => message.role === 'user');
}

function applyProblemSwitch(newProblemId, resetConversation) {
  state.problemId = newProblemId;
  state.problemTitle = null;
  state.pendingProblemSwitch = null;
  if (resetConversation) {
    state.messages = [];
    state.stage = null;
    state.hintLevel = null;
    state.submissionResult = null;
    state.activeChip = null;
    state.input = '';
    state.lastMessageStage = null;
    ensureWelcomeMessage();
  }
  renderShell();
  syncPageContext().then(() => renderShell());
}

function handleKeepConversationOnProblemSwitch() {
  if (!state.pendingProblemSwitch) {
    return;
  }
  applyProblemSwitch(state.pendingProblemSwitch.problemId, false);
}

function handleStartFreshOnProblemSwitch() {
  if (!state.pendingProblemSwitch) {
    return;
  }
  applyProblemSwitch(state.pendingProblemSwitch.problemId, true);
}

function refreshActiveTabStatus() {
  queryActiveTab().then((activeTab) => {
    const onProgrammers = isTabOnProgrammers(activeTab);
    const urlProblemId = onProgrammers ? parseProblemIdFromUrl(activeTab.url) : null;
    const navigatedToDifferentProblem = onProgrammers && urlProblemId != null && urlProblemId !== state.problemId
      && (!state.pendingProblemSwitch || state.pendingProblemSwitch.problemId !== urlProblemId);
    // 확인 다이얼로그를 띄운 채로 사용자가 원래 보던 문제로 되돌아가면
    // 더 이상 물어볼 이유가 없으니 조용히 취소한다.
    const backToOriginalProblem = state.pendingProblemSwitch && urlProblemId === state.problemId;

    let shouldRender = false;

    if (onProgrammers !== state.onProgrammers) {
      state.onProgrammers = onProgrammers;
      // 확인 대기 중에 프로그래머스를 완전히 벗어나면, renderShell()이 onProgrammers
      // 여부와 무관하게 pendingProblemSwitch를 최우선으로 그려버려서 오프사이트
      // 안내/비활성화 화면 대신 엉뚱하게 확인 카드가 계속 떠 있게 된다. 물어볼
      // 대상 페이지 자체를 벗어났으니 조용히 취소한다.
      if (!onProgrammers && state.pendingProblemSwitch) {
        state.pendingProblemSwitch = null;
      }
      shouldRender = true;
    }

    if (navigatedToDifferentProblem) {
      // 대화가 없는 상태(웰컴 메시지뿐이거나 방금 초기화됨)라면 잃을 게 없으니
      // 굳이 확인받지 않고 바로 반영한다. 확인이 필요한 건 진행 중인 대화가
      // 있을 때뿐이다.
      if (hasActiveConversation()) {
        state.pendingProblemSwitch = { problemId: urlProblemId };
      } else {
        applyProblemSwitch(urlProblemId, false);
      }
      shouldRender = true;
    } else if (backToOriginalProblem) {
      state.pendingProblemSwitch = null;
      shouldRender = true;
    }

    if (shouldRender) {
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

  // 패널을 연 시점에 실제로 프로그래머스 탭을 보고 있는지 먼저 확인한다.
  // problemId/gradingResult는 다른 사이트로 넘어가도 storage에 마지막 문제
  // 상태로 계속 남아있어서, 이 확인 없이는 웰컴 메시지/헤더에 예전 문제 번호가
  // 그대로 뜨고 예전 채점 결과가 방금 감지된 것처럼 재생돼버린다.
  const initialActiveTab = await queryActiveTab();
  state.onProgrammers = isTabOnProgrammers(initialActiveTab);

  ensureWelcomeMessage();
  if (pendingGradingResult) {
    applyGradingResult(pendingGradingResult);
  }

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
      const newProblemId = changes.problemId.newValue ?? null;
      // content.js가 CODE_CHANGED로 새 문제 id를 실시간 보고할 때도(코드 에디터를
      // 스치기만 해도 발생) refreshActiveTabStatus()의 URL 기반 감지와 동일하게
      // 진행 중인 대화가 있으면 확인을 받는다. 여기서 직접 덮어써버리면 그
      // 확인 절차를 완전히 우회하게 된다.
      if (newProblemId != null && newProblemId !== state.problemId
        && (!state.pendingProblemSwitch || state.pendingProblemSwitch.problemId !== newProblemId)) {
        if (hasActiveConversation()) {
          state.pendingProblemSwitch = { problemId: newProblemId };
        } else {
          applyProblemSwitch(newProblemId, false);
        }
      } else if (newProblemId === state.problemId) {
        state.pendingProblemSwitch = null;
      }
    }

    if (changes.problemTitle && !state.pendingProblemSwitch) {
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
