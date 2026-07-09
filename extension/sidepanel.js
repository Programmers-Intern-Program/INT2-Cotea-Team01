const DEFAULT_API_CONFIG = {
  mode: 'mock',
  baseUrl: '',
  endpoint: '/api/hint',
};

const AVATAR_URL = chrome.runtime.getURL('mascot.png');

const CHIPS = [
  { label: '접근 방식 힌트', resp: 'DFS 재귀로 각 인덱스마다 +/−를 분기하고, 배열 끝에서 target과 비교하는 구조예요. 반환값을 합산하는 방식이 가장 깔끔합니다.' },
  { label: '자료구조 추천', resp: '별도 자료구조는 불필요해요. 재귀 스택 자체가 DFS 역할을 합니다. BFS라면 ArrayDeque 같은 큐 구조를 쓰면 돼요.' },
  { label: '시간복잡도 분석', resp: 'O(2^n) 구조예요. 각 숫자마다 두 갈래로 분기하므로 숫자가 20개면 최대 약 100만 번 정도 탐색합니다.' },
];

const SAMPLE_CODE = `class Solution {\n    int answer = 0;\n\n    public int solution(int[] numbers,\n                        int target) {\n        dfs(numbers, target, 0, 0);\n        return answer;\n    }\n\n    void dfs(int[] numbers, int target,\n             int index, int current) {\n        if (index == numbers.length) {\n            if (current == target)\n                answer++;\n            return;\n        }\n        // ← 이 줄을 확인해 보세요!\n        dfs(numbers, target, index + 1,\n            current + numbers[index]);\n        dfs(numbers, target, index + 1,\n            current - numbers[index]);\n    }\n}`;

const state = {
  messages: [
    {
      id: 1,
      role: 'ai',
      text: '안녕하세요! 저는 Cotea예요.\n\n오늘의 문제 "타겟 넘버"를 함께 풀어봐요. 각 숫자에 +/− 부호를 붙여 target을 만드는 경우의 수를 구하는 완전탐색 문제예요.\n\n어떤 방식으로 시작해보실 건가요?',
      timestamp: '10:24',
    },
    {
      id: 2,
      role: 'user',
      text: 'DFS로 재귀 짜봤는데 결과가 항상 0이 나와요.',
      timestamp: '10:31',
    },
    {
      id: 3,
      role: 'ai',
      text: '코드를 분석해봤어요. 15번째 줄에서 반환값 처리에 문제가 있어 보여요.',
      timestamp: '10:31',
      code: { src: SAMPLE_CODE, hl: 14 },
      trailChips: ['이유가 뭔가요?', '어떻게 고쳐야 할까요?'],
    },
    {
      id: 4,
      role: 'divider',
      text: '풀이 중 단계로 넘어왔어요!',
    },
  ],
  input: '자료구조 추천',
  activeChip: '자료구조 추천',
  busy: false,
  latestCode: '',
  apiConfig: { ...DEFAULT_API_CONFIG },
  settingsOpen: false,
  saveState: '',
};

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
  return state.latestCode
    ? '문제: 프로그래머스 실시간 코드 분석'
    : '문제: 프로그래머스 Lv2 "타겟 넘버"';
}

function renderSyncLabel() {
  return state.latestCode ? '최신 코드 동기화 완료' : '코드 동기화 대기 중';
}

function renderSettingsPanel() {
  if (!state.settingsOpen) {
    return '';
  }

  return `
    <section class="settings-panel">
      <div class="settings-grid">
        <label class="settings-field">
          <span>호출 모드</span>
          <select id="mode-select">
            <option value="mock" ${state.apiConfig.mode === 'mock' ? 'selected' : ''}>mock</option>
            <option value="api" ${state.apiConfig.mode === 'api' ? 'selected' : ''}>api</option>
          </select>
        </label>
        <label class="settings-field">
          <span>Base URL</span>
          <input id="base-url-input" type="text" value="${escapeHtml(state.apiConfig.baseUrl)}" placeholder="https://api.example.com">
        </label>
        <label class="settings-field">
          <span>Endpoint</span>
          <input id="endpoint-input" type="text" value="${escapeHtml(state.apiConfig.endpoint)}" placeholder="/api/hint">
        </label>
      </div>
      <div class="settings-footer">
        <button type="button" class="settings-save" id="save-settings-button">설정 저장</button>
        <span class="settings-state">${escapeHtml(state.saveState)}</span>
      </div>
    </section>
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
            <button type="button" id="settings-toggle" class="header-action">${state.settingsOpen ? '설정 닫기' : 'API 설정'}</button>
          </div>
        </header>

        <section class="cotea-chat-scroll" id="chat-scroll">
          ${state.messages.map(renderMessage).join('')}
          ${renderBusyIndicator()}
        </section>

        <div class="cotea-bottom-shell">
          ${renderSettingsPanel()}
          <div class="prompt-chip-row">
            ${CHIPS.map((chip) => {
              const active = state.activeChip === chip.label;
              return `<button type="button" class="prompt-chip ${active ? 'active' : ''}" data-chip="${escapeHtml(chip.label)}">${escapeHtml(chip.label)}</button>`;
            }).join('')}
          </div>

          <div class="sync-row">
            <span class="sync-dot ${state.latestCode ? 'ok' : ''}"></span>
            <span class="sync-label">${escapeHtml(renderSyncLabel())}</span>
          </div>

          <div class="composer-row ${state.activeChip && state.input === state.activeChip ? 'caret-mode' : ''}">
            <div class="composer-input-wrap">
              <input id="question-input" type="text" value="${escapeHtml(state.input)}" placeholder="Cotea에게 질문하세요..." ${state.busy ? 'disabled' : ''}>
              ${state.activeChip && state.input === state.activeChip ? `<div class="fake-caret-layer"><span class="ghost-text">${escapeHtml(state.input)}</span><span class="fake-caret"></span></div>` : ''}
            </div>
            <button type="button" id="send-button" class="send-button" ${!state.input.trim() || state.busy ? 'disabled' : ''}>
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
  const settingsToggle = document.getElementById('settings-toggle');
  if (settingsToggle) {
    settingsToggle.addEventListener('click', () => {
      state.settingsOpen = !state.settingsOpen;
      renderShell();
    });
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

  const modeSelect = document.getElementById('mode-select');
  if (modeSelect) {
    modeSelect.addEventListener('change', (event) => {
      state.apiConfig.mode = event.target.value;
    });
  }

  const baseUrlInput = document.getElementById('base-url-input');
  if (baseUrlInput) {
    baseUrlInput.addEventListener('input', (event) => {
      state.apiConfig.baseUrl = event.target.value.trim();
    });
  }

  const endpointInput = document.getElementById('endpoint-input');
  if (endpointInput) {
    endpointInput.addEventListener('input', (event) => {
      state.apiConfig.endpoint = event.target.value;
    });
  }

  const saveSettingsButton = document.getElementById('save-settings-button');
  if (saveSettingsButton) {
    saveSettingsButton.addEventListener('click', saveSettings);
  }
}

async function saveSettings() {
  state.saveState = '저장 중...';
  renderShell();

  try {
    await sendRuntimeMessage({
      type: 'SET_API_CONFIG',
      payload: state.apiConfig,
    });
    state.saveState = '저장됨';
  } catch (error) {
    state.saveState = `저장 실패: ${error.message}`;
  }

  renderShell();
}

async function handleSend() {
  const question = state.input.trim();
  if (!question || state.busy) {
    return;
  }

  state.messages.push({
    id: Date.now(),
    role: 'user',
    text: question,
    timestamp: nowLabel(),
  });
  state.input = '';
  state.activeChip = null;
  state.busy = true;
  renderShell();

  try {
    const response = await sendRuntimeMessage({
      type: 'ASK_AI',
      code: state.latestCode,
      question,
    });

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

async function initialize() {
  try {
    const response = await sendRuntimeMessage({ type: 'GET_PANEL_STATE' });
    state.latestCode = response && response.latestCode ? response.latestCode : '';
    state.apiConfig = { ...DEFAULT_API_CONFIG, ...((response && response.apiConfig) || {}) };
  } catch (error) {
    state.messages.push({
      id: Date.now() + 3,
      role: 'ai',
      text: `초기 상태를 불러오지 못했습니다. ${error.message}`,
      timestamp: nowLabel(),
    });
  }

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName !== 'local') {
      return;
    }

    if (changes.latestCode) {
      state.latestCode = changes.latestCode.newValue || '';
    }

    if (changes.apiConfig) {
      state.apiConfig = { ...DEFAULT_API_CONFIG, ...(changes.apiConfig.newValue || {}) };
    }

    renderShell();
  });

  renderShell();
}

initialize();
