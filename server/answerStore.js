// File-backed store for user-submitted quest answers (기획문서 2.5's "다른 사람
// 한마디" pool). A plain JSON file is enough at this project's scale — no
// npm dependency, no native module to compile on the small VM this runs on.
// If submission volume ever justifies it, this is the seam to swap in a
// real database without touching the moderation logic in moderation.js.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.join(__dirname, 'data');
const DATA_FILE = path.join(DATA_DIR, 'submissions.json');

function ensureFile() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, '[]');
}

function readAll() {
  ensureFile();
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  } catch {
    return []; // corrupt file shouldn't take the server down — start fresh
  }
}

function writeAll(entries) {
  ensureFile();
  fs.writeFileSync(DATA_FILE, JSON.stringify(entries, null, 2));
}

let nextId = null;
function allocateId(entries) {
  if (nextId === null) {
    nextId = entries.reduce((max, e) => Math.max(max, e.id), 0) + 1;
  }
  return nextId++;
}

export function addSubmission({ quest, question, answer, status, reason }) {
  const entries = readAll();
  const entry = {
    id: allocateId(entries),
    quest,
    question,
    answer,
    status, // 'approved' | 'rejected' | 'pending'
    reason: reason || null,
    ts: new Date().toISOString(),
  };
  entries.push(entry);
  writeAll(entries);
  return entry;
}

export function listPending() {
  return readAll().filter((e) => e.status === 'pending');
}

export function decide(id, decision) {
  const entries = readAll();
  const entry = entries.find((e) => e.id === id);
  if (!entry) return null;
  entry.status = decision; // 'approved' | 'rejected'
  writeAll(entries);
  return entry;
}

export function randomApprovedAnswer(quest) {
  const candidates = readAll().filter((e) => e.status === 'approved' && e.quest === quest);
  if (candidates.length === 0) return null;
  return candidates[Math.floor(Math.random() * candidates.length)];
}
