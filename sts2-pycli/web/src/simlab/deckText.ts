/**
 * Pure helpers for the deck-variant editor's textarea <-> card-list conversion.
 *
 * The textarea is one card id per line, with an optional `Nx ` count prefix
 * (e.g. `5x STRIKE_IRONCLAD`). None of these functions throw; parse problems
 * are surfaced as `errors` strings naming the offending line.
 */

/**
 * Matches a count-prefixed line: an optional integer count, the `x`/`X`
 * separator, then the rest. A missing count (`x FOO`) or non-positive count
 * (`0x FOO`) is captured here too so it can be reported as an error rather
 * than silently treated as a bare card id.
 */
const COUNT_PREFIX = /^(-?\d+)?\s*[xX]\s+(.+)$/;

/**
 * Parse one-card-per-line text into a flat card-id list.
 *
 * Supports the `Nx CARD` shorthand (expands to N copies). Blank or
 * whitespace-only lines are ignored. A non-positive count, or a `Nx` prefix
 * with no id, yields an entry in `errors` (naming the trimmed line) and
 * contributes no cards. Never throws.
 */
export function parseDeckText(text: string): { cards: string[]; errors: string[] } {
  const cards: string[] = [];
  const errors: string[] = [];
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (line === "") continue;
    const m = COUNT_PREFIX.exec(line);
    if (m) {
      const count = m[1] === undefined ? NaN : Number(m[1]);
      const id = m[2].trim();
      if (!Number.isInteger(count) || count <= 0 || id === "") {
        errors.push(`invalid line: "${line}"`);
        continue;
      }
      for (let i = 0; i < count; i++) cards.push(id);
      continue;
    }
    cards.push(line);
  }
  return { cards, errors };
}

/**
 * Inverse of {@link parseDeckText}: group runs of consecutive-equal ids into
 * `Nx CARD` lines (only when N > 1), bare lines otherwise. Order preserved.
 */
export function formatDeck(cards: string[]): string {
  const lines: string[] = [];
  let i = 0;
  while (i < cards.length) {
    const id = cards[i];
    let n = 1;
    while (i + n < cards.length && cards[i + n] === id) n++;
    lines.push(n > 1 ? `${n}x ${id}` : id);
    i += n;
  }
  return lines.join("\n");
}

/**
 * Return the card ids whose base (after stripping a trailing `+N` upgrade
 * suffix) is not in `known`. Order-preserving and deduped.
 */
export function unknownCards(cards: string[], known: string[]): string[] {
  const knownSet = new Set(known);
  const seen = new Set<string>();
  const out: string[] = [];
  for (const id of cards) {
    const base = id.replace(/\+\d+$/, "");
    if (knownSet.has(base)) continue;
    if (seen.has(id)) continue;
    seen.add(id);
    out.push(id);
  }
  return out;
}
