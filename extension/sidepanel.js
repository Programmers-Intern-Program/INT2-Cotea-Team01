const DEFAULT_API_CONFIG = {
  mode: 'api',
  baseUrl: 'http://localhost:8080',
  endpoint: '/api/hint',
};

const DEFAULT_PROBLEM_ID = 1829;

const AVATAR_URL = chrome.runtime.getURL('mascot.png');

const STAGE_OPTIONS = [
  { value: 'BEFORE_SOLVE', label: '풀기 전', colorClass: 'stage-before-solve' },
  { value: 'SOLVING', label: '풀이 중', colorClass: 'stage-solving' },
  { value: 'WRONG_ANSWER', label: '오답이에요', colorClass: 'stage-wrong-answer' },
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
  stagePickerOpen: true,
  hintLevel: null,
  submissionResult: null,
  apiConfig: { ...DEFAULT_API_CONFIG },
  syncing: false,
  codeDirty: false,
  onProgrammers: true,
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
  if (!state.stage) {
    return false;
  }
  if (state.stage === 'BEFORE_SOLVE' && !state.hintLevel) {
    return false;
  }
  if (state.stage === 'WRONG_ANSWER' && !state.submissionResult) {
    return false;
  }
  return true;
}

function pushStageDivider(label) {
  state.messages.push({ id: Date.now(), role: 'divider', text: label });
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
    conversationHistory: buildConversationHistory(),
  };

  if (stage === 'WRONG_ANSWER') {
    base.submissionResult = state.submissionResult;
  }

  if (chip && chipLabel === questionText) {
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
    if (response && response.code) {
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
  const bubbleBody = message.glow
    ? '<div class="typing-row"><span class="dot"></span><span class="dot"></span><span class="dot"></span><span class="typing-label">분석 중이에요...</span></div>'
    : `${renderRichText(message.text)}${message.code ? renderCodeBlock(message.code.src, message.code.hl) : ''}`;

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
        ${message.timestamp && !message.glow ? `<span class="message-time">${escapeHtml(message.timestamp)}</span>` : ''}
      </div>
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
  if (state.stage === 'BEFORE_SOLVE' && !state.hintLevel) {
    return '힌트 레벨을 선택해주세요';
  }
  if (state.stage === 'WRONG_ANSWER' && !state.submissionResult) {
    return '채점 결과를 선택해주세요';
  }
  return 'Cotea에게 질문하세요...';
}

function renderStageSelector() {
  if (state.stage && !state.stagePickerOpen) {
    const opt = STAGE_OPTIONS.find((o) => o.value === state.stage);
    return `
      <div class="stage-select-row">
        <button type="button" class="stage-chip current ${opt ? opt.colorClass : ''}" data-stage-change ${!state.onProgrammers || state.busy ? 'disabled' : ''}>
          상태: ${escapeHtml(opt ? opt.label : state.stage)}<span class="stage-chip-edit">변경</span>
        </button>
      </div>
    `;
  }

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

function renderShell() {
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
            <button type="button" id="sync-button" class="header-action sync-button ${state.syncing ? 'syncing' : ''}" aria-label="코드 동기화" data-tooltip="코드 동기화" ${state.syncing || !state.onProgrammers ? 'disabled' : ''}>
              <svg class="sync-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="23 4 23 10 17 10"></polyline>
                <polyline points="1 20 1 14 7 14"></polyline>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
              </svg>
            </button>
          </div>
        </header>

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

          <div class="composer-row ${state.activeChip && state.input === state.activeChip ? 'caret-mode' : ''}">
            <div class="composer-input-wrap">
              <input id="question-input" type="text" value="${escapeHtml(state.input)}" placeholder="${escapeHtml(renderComposerPlaceholder())}" ${state.busy || !state.onProgrammers || !isComposerReady() ? 'disabled' : ''}>
              ${state.activeChip && state.input === state.activeChip ? `<div class="fake-caret-layer"><span class="ghost-text">${escapeHtml(state.input)}</span><span class="fake-caret"></span></div>` : ''}
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
      </div>
    </div>
  `;

  bindEvents();

  const chatScroll = document.getElementById('chat-scroll');
  if (chatScroll) {
    chatScroll.scrollTop = chatScroll.scrollHeight;
  }
}

function bindEvents() {
  const syncButton = document.getElementById('sync-button');
  if (syncButton) {
    syncButton.addEventListener('click', handleSync);
  }

  const questionInput = document.getElementById('question-input');
  if (questionInput) {
    questionInput.addEventListener('input', (event) => {
      state.input = event.target.value;
      if (state.input !== state.activeChip) {
        state.activeChip = null;
      }
      renderShell();
    });

    questionInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        handleSend();
      }
    });
  }

  const sendButton = document.getElementById('send-button');
  if (sendButton) {
    sendButton.addEventListener('click', handleSend);
  }

  document.querySelectorAll('[data-chip]').forEach((button) => {
    button.addEventListener('click', () => {
      const label = button.dataset.chip || '';
      state.input = label;
      state.activeChip = label;
      renderShell();
      const input = document.getElementById('question-input');
      if (input) {
        input.focus();
      }
    });
  });

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

  const stageChangeButton = document.querySelector('[data-stage-change]');
  if (stageChangeButton) {
    stageChangeButton.addEventListener('click', () => {
      if (state.busy) {
        return;
      }
      state.stagePickerOpen = true;
      renderShell();
    });
  }

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
}

function handleStageSelect(value) {
  if (state.busy) {
    return;
  }
  if (state.stage === value) {
    state.stagePickerOpen = false;
    renderShell();
    return;
  }
  state.stage = value;
  state.stagePickerOpen = false;
  state.hintLevel = null;
  state.submissionResult = null;
  state.activeChip = null;
  state.input = '';
  pushStageDivider(STAGE_LABEL[value] || value);
  renderShell();
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

    if (response && response.source === 'error') {
      throw new Error(response.answer || '응답 생성 중 오류가 발생했습니다.');
    }

    state.messages.push({
      id: Date.now() + 1,
      role: 'ai',
      text: response && response.answer ? response.answer : '응답을 받지 못했습니다.',
      timestamp: nowLabel(),
    });
  } catch (error) {
    state.messages.push({
      id: Date.now() + 2,
      role: 'ai',
      text: `요청 중 오류가 발생했습니다. ${error.message}`,
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
  try {
    const response = await sendRuntimeMessage({ type: 'GET_PANEL_STATE' });
    state.latestCode = response && response.latestCode ? response.latestCode : '';
    state.problemId = response && response.problemId != null ? response.problemId : null;
    state.problemTitle = response && response.problemTitle ? response.problemTitle : null;
    state.apiConfig = { ...DEFAULT_API_CONFIG, ...((response && response.apiConfig) || {}) };
    state.codeDirty = Boolean(response && response.codeDirty);
  } catch (error) {
    state.messages.push({
      id: Date.now() + 3,
      role: 'ai',
      text: `초기 상태를 불러오지 못했습니다. ${error.message}`,
      timestamp: nowLabel(),
    });
  }

  await syncPageContext();
  ensureWelcomeMessage();
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

    if (changes.codeDirty) {
      state.codeDirty = Boolean(changes.codeDirty.newValue);
    }

    renderShell();
  });

  renderShell();
}

initialize();
