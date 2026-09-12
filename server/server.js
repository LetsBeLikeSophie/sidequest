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

// 기획문서 2.4: 10~18자, 담백한 톤, 절대 평가·분석·조언·칭찬 금지.
const SYSTEM_PROMPT =
  '사용자가 오늘의 작은 행동에 대해 남긴 한마디에 아주 짧게 반응한다. ' +
  '10~18자 내외의 담백한 한 문장만 반환한다. ' +
  '절대 평가하거나 분석하거나 조언하거나 칭찬하지 않는다. ' +
  '그저 듣고 접수했다는 느낌만 준다. 예: "오, 좋네요.", "기록해뒀어요.", "그렇군요."';

function isRelevant(note) {
  const trimmed = note.trim();
  if (trimmed.length < 2) return false;
  if (/^[\s\W_]+$/u.test(trimmed)) return false; // symbols/whitespace only
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

const server = http.createServer(async (req, res) => {
  if (req.method === 'POST' && req.url === '/api/react') {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', async () => {
      try {
        const { note } = JSON.parse(body || '{}');
        if (typeof note !== 'string' || !isRelevant(note)) {
          res.writeHead(200, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ reaction: null }));
          return;
        }
        const reaction = await getReaction(note);
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ reaction }));
      } catch (err) {
        console.error(err);
        res.writeHead(500, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ error: 'internal_error' }));
      }
    });
    return;
  }

  res.writeHead(404, { 'content-type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
});

if (!API_KEY) {
  console.error('ANTHROPIC_API_KEY is not set — check ../.env or pass it in the environment.');
  process.exit(1);
}

server.listen(PORT, () => {
  console.log(`side quest reaction proxy listening on http://localhost:${PORT}`);
});
