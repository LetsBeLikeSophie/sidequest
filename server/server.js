// Minimal server-side proxy for the "짧은 반응" feature (기획문서 2.4).
//
// The whole point of this file existing is that the Anthropic API key never
// reaches the Android app or any web prototype — it lives here, server-side,
// and the client only ever talks to this proxy's /api/react endpoint.
//
// Zero npm dependencies on purpose: Node 18+ ships a global fetch, and a
// plain http.createServer is enough for one endpoint. Run with:
//   node --env-file=../.env server.js

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { moderate } from './moderation.js';
import { addSubmission, listPending, decide, randomApprovedAnswer } from './answerStore.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Fall back to reading ../.env by hand, in case this wasn't started with
// --env-file (e.g. an older Node, or a process manager that doesn't pass it).
function loadDotEnvFallback() {
  if (process.env.ANTHROPIC_API_KEY) return;
  const envPath = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const match = line.match(/^\s*([\w.-]+)\s*=\s*(.*)\s*$/);
    if (match) process.env[match[1]] ||= match[2];
  }
}
loadDotEnvFallback();

const API_KEY = process.env.ANTHROPIC_API_KEY;
const PORT = process.env.PORT || 8787;
const MODEL = 'claude-sonnet-5';
// Only for the /api/review/* endpoints below — this is a solo-dev moderation
// queue, not a real admin system, so a shared-secret header is enough.
const ADMIN_TOKEN = process.env.ADMIN_TOKEN;

// 기획문서 2.4: 10~18자, 담백한 톤, 절대 평가·분석·조언·칭찬 금지.
const SYSTEM_PROMPT =
  '사용자가 오늘의 작은 행동에 대해 남긴 한마디에 아주 짧게 반응한다. ' +
  '10~18자 내외의 담백한 한 문장만 반환한다. ' +
  '절대 평가하거나 분석하거나 조언하거나 칭찬하지 않는다. ' +
  '그저 듣고 접수했다는 느낌만 준다. 예: "오, 좋네요.", "기록해뒀어요.", "그렇군요."';

function isRelevant(note) {
  const trimmed = note.trim();
  if (trimmed.length < 2) return false;
  // \w in JS regex is ASCII-only, so a naive "reject symbols-only" check
  // using \W would treat pure-Hangul text as "symbols" and silently drop
  // every Korean-only note. \p{L}/\p{N} (with the u flag) are Unicode-aware,
  // so this actually just asks "is there at least one real letter or digit,
  // in any script?" instead.
  if (!/\p{L}|\p{N}/u.test(trimmed)) return false; // truly nothing but symbols/whitespace
  return true;
}

async function getReaction(note) {
  // 55% 확률로만 반응 — 매번 오면 "잘 써야 반응 오겠지" 하는 압박이 생긴다 (2.4)
  if (Math.random() >= 0.55) return null;

  const response = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'x-api-key': API_KEY,
      'anthropic-version': '2023-06-01',
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: 40,
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content: note }],
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Anthropic API ${response.status}: ${body}`);
  }

  const data = await response.json();
  const text = data.content?.[0]?.text?.trim();
  return text || null;
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      try { resolve(JSON.parse(body || '{}')); }
      catch (err) { reject(err); }
    });
    req.on('error', reject);
  });
}

function sendJson(res, status, obj) {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(obj));
}

function isAuthorized(req) {
  return Boolean(ADMIN_TOKEN) && req.headers['x-admin-token'] === ADMIN_TOKEN;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');

  try {
    if (req.method === 'POST' && url.pathname === '/api/react') {
      const { note } = await readJsonBody(req);
      if (typeof note !== 'string' || !isRelevant(note)) {
        return sendJson(res, 200, { reaction: null });
      }
      const reaction = await getReaction(note);
      return sendJson(res, 200, { reaction });
    }

    // 기획문서 2.5: 사용자가 답한 한마디를 "다른 사람 답변" 풀에 넣기 전에
    // 검수한다. 어떤 판정이 나왔는지는 절대 응답에 담지 않는다 — 제출한
    // 사람이 "내 글이 반려됐다"는 걸 알게 되는 순간 그 자체로 평가받는
    // 느낌이 생기기 때문에, 접수 자체는 언제나 조용히 성공으로 처리한다.
    if (req.method === 'POST' && url.pathname === '/api/submit-answer') {
      const { quest, question, answer } = await readJsonBody(req);
      if (typeof answer !== 'string' || !isRelevant(answer) ||
          typeof quest !== 'string' || typeof question !== 'string') {
        return sendJson(res, 200, { ok: true });
      }
      const { status, reason } = await moderate(API_KEY, MODEL, answer);
      addSubmission({ quest, question, answer, status, reason });
      return sendJson(res, 200, { ok: true });
    }

    if (req.method === 'GET' && url.pathname === '/api/random-answer') {
      const quest = url.searchParams.get('quest') || '';
      const entry = randomApprovedAnswer(quest);
      return sendJson(res, 200, { answer: entry?.answer ?? null });
    }

    if (req.method === 'GET' && url.pathname === '/api/review/pending') {
      if (!isAuthorized(req)) return sendJson(res, 401, { error: 'unauthorized' });
      return sendJson(res, 200, { pending: listPending() });
    }

    if (req.method === 'POST' && url.pathname === '/api/review/decide') {
      if (!isAuthorized(req)) return sendJson(res, 401, { error: 'unauthorized' });
      const { id, decision } = await readJsonBody(req);
      if (decision !== 'approved' && decision !== 'rejected') {
        return sendJson(res, 400, { error: 'invalid_decision' });
      }
      const entry = decide(id, decision);
      if (!entry) return sendJson(res, 404, { error: 'not_found' });
      return sendJson(res, 200, { ok: true, entry });
    }

    return sendJson(res, 404, { error: 'not_found' });
  } catch (err) {
    console.error(err);
    return sendJson(res, 500, { error: 'internal_error' });
  }
});

if (!API_KEY) {
  console.error('ANTHROPIC_API_KEY is not set — check ../.env or pass it in the environment.');
  process.exit(1);
}

server.listen(PORT, () => {
  console.log(`side quest reaction proxy listening on http://localhost:${PORT}`);
});
