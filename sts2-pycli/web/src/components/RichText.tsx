import type { ReactNode } from "react";

// STS2 inline color codes used in event/option text, e.g. "[red]6[/red] HP".
const COLOR: Record<string, string> = {
  red: "var(--hp)",
  gold: "var(--gold)",
  blue: "var(--block)",
  purple: "oklch(70% 0.13 300)",
  green: "oklch(72% 0.14 150)",
};

// Decoration/animation tags the game layers on its loc strings ([b]cheese[/b],
// [jitter]cheese!!![/jitter], [sine]Time passes...[/sine]). We render [b] as
// bold and drop the pure-animation wrappers, keeping their inner text.
const STRIP = new Set(["jitter", "sine", "shake", "wave", "rainbow", "i", "u"]);

// Capturing split keeps the tags in the resulting array: [text, tag, text, ...].
const SPLIT = /(\[\/?[a-z]+\])/g;
const TAG = /^\[(\/?)([a-z]+)\]$/;

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** Build a capturing whole-word matcher for the display-only emphasis pass.
 *  Capturing so `String.split` keeps the matched words in the result array. */
function emphasisRe(words: string[]): RegExp | null {
  const parts = words.map((w) => w.trim()).filter((w) => w.length >= 3).map(escapeRe);
  if (parts.length === 0) return null;
  return new RegExp(`(\\b(?:${parts.join("|")})\\b)`, "gi");
}

/** Render text with STS2 ``[color]``/``[b]`` codes as styled spans; unknown
 *  animation wrappers are stripped, any other bracketed text passes through.
 *
 *  ``emphasize`` is a DISPLAY-ONLY pass: every whole-word occurrence of the
 *  listed words is wrapped in ``<strong>``. The wire's event strings arrive
 *  with their ``[b]…[/b]`` markup already flattened away, so the game's
 *  bold-keyword rhythm ("This room is full of **cheese**!!!") cannot be
 *  recovered from the payload; the caller supplies the words it can justify
 *  (see ``EventScreen.emphasisWords``). No word is ever added, removed or
 *  rewritten — only wrapped. */
export default function RichText({
  text,
  emphasize,
}: {
  text?: string | null;
  emphasize?: string[];
}) {
  if (!text) return null;
  const re = emphasize && emphasize.length > 0 ? emphasisRe(emphasize) : null;
  const out: ReactNode[] = [];
  let color: string | null = null;
  let bold = 0;
  text.split(SPLIT).forEach((part, i) => {
    if (!part) return;
    const tag = part.match(TAG);
    if (tag) {
      const closing = tag[1] === "/";
      const name = tag[2];
      if (name in COLOR) {
        color = closing ? null : COLOR[name];
        return;
      }
      if (name === "b") {
        bold = Math.max(0, bold + (closing ? -1 : 1));
        return;
      }
      if (STRIP.has(name)) return; // animation-only wrapper: keep inner text
      // Unknown bracketed token: fall through and render it literally.
    }
    const style: Record<string, string> = {};
    if (color) style.color = color;
    if (bold > 0) style.fontWeight = "700";
    // Split the run on the emphasis words; the capturing group puts them at
    // the odd indices of the resulting array.
    const bits = re ? part.split(re) : [part];
    const body: ReactNode =
      bits.length > 1
        ? bits.map((b, j) => (j % 2 === 1 ? <strong key={j}>{b}</strong> : b))
        : part;
    out.push(
      Object.keys(style).length > 0 ? (
        <span key={i} style={style}>{body}</span>
      ) : (
        <span key={i}>{body}</span>
      ),
    );
  });
  return <>{out}</>;
}
