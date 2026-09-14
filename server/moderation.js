// 기획문서 6, 8.3: "익명 코멘트 모더레이션은 일회성이 아니라 지속적으로 보정해야
// 하는 운영 항목" — and 3.1's own content pipeline (regex filter first, LLM
// review for anything ambiguous) is the same shape a *user-submitted* answer
// needs, so it's reused here rather than inventing a separate scheme.
//
// Two stages, cheapest first:
//   1. Regex catches unambiguous PII (email/phone) — reject immediately,
//      no API call spent on something a pattern already answers.
//   2. Everything else goes to Claude with a narrow moderation-only prompt
//      (SAFE/UNSAFE, nothing else) — handles the abuse/spam/irrelevance
//      cases a fixed keyword list can't keep up with, since real user
//      phrasing is unpredictable and keyword lists go stale.
//
// Every verdict is persisted by the caller (see answerStore) with the raw
// text and reason, specifically so the prompt below can be tuned against
// real misses later instead of guessing blind today.

const EMAIL_RE = /[\w.+-]+@[\w-]+\.[\w.-]+/;
const PHONE_RE = /(\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{4}/;
const URL_RE = /https?:\/\/|www\./i;

// Deliberately narrow: only true safety violations, not "is this relevant
// to the quest" — that turned out to make the model reject harmless answers
// as UNSAFE whenever it couldn't verify relevance (see moderate()'s comment
// for why quest/question context isn't even passed here). Off-topic-but-
// harmless text is noise, not a safety problem, so it's not this
// function's job to catch it.
const MODERATION_SYSTEM_PROMPT =
  '너는 익명 커뮤니티에 올라갈 짧은 한마디를 검수하는 필터다. ' +
  '아래 텍스트에 욕설·비속어·모욕·혐오 표현·성적인 내용·스팸·광고·개인정보(실명/전화번호/' +
  '주소 등 특정 개인을 식별할 수 있는 정보)가 있으면 UNSAFE, 없으면 SAFE라고 답한다. ' +
  '내용이 특별할 것 없이 평범하거나 심심하다는 이유만으로 UNSAFE로 판단하지 않는다 — ' +
  '위에 나열된 항목에 실제로 해당하는 경우에만 UNSAFE다. SAFE 또는 UNSAFE 둘 중 ' +
  '한 단어만 답하고 다른 말은 절대 덧붙이지 않는다.';

export function findObviousPII(text) {
  if (EMAIL_RE.test(text)) return 'email';
  if (PHONE_RE.test(text)) return 'phone';
  if (URL_RE.test(text)) return 'url';
  return null;
}

export async function classifyWithClaude(apiKey, model, text) {
  const response = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model,
      max_tokens: 5,
      // Note: `temperature` is deprecated/rejected by this model — can't force
      // determinism that way, so the "when in doubt, UNSAFE" instruction in
      // the prompt is what has to carry consistency here instead.
      system: MODERATION_SYSTEM_PROMPT,
      messages: [{ role: 'user', content: text }],
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Anthropic API ${response.status}: ${body}`);
  }

  const data = await response.json();
  const verdict = data.content?.[0]?.text?.trim().toUpperCase();
  return verdict === 'SAFE'; // anything that isn't a clean SAFE is treated as unsafe
}

/** Returns { status, reason } — status is 'approved' | 'rejected' | 'pending'. */
export async function moderate(apiKey, model, text) {
  const piiHit = findObviousPII(text);
  if (piiHit) return { status: 'rejected', reason: `regex:${piiHit}` };

  try {
    const safe = await classifyWithClaude(apiKey, model, text);
    return safe
      ? { status: 'approved', reason: 'llm:safe' }
      : { status: 'rejected', reason: 'llm:unsafe' };
  } catch (err) {
    // If the classifier itself fails, don't guess — hold it for a human
    // to look at rather than either silently publishing or silently losing it.
    return { status: 'pending', reason: `llm_error:${err.message}` };
  }
}
