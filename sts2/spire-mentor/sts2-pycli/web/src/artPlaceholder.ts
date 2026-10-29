/**
 * Placeholder art for a checkout without the extracted game images.
 *
 * The public repository ships the game's text and data but no art: a card,
 * relic, monster or background whose file was never extracted (`just
 * import-game`) 404s. Rather than thread a fallback through the ~70 `<img>`
 * sites, one capturing `error` listener on the document swaps any failed
 * image under the art base for a neutral inline SVG tile, once. Components
 * with their own `onError` (rest-site art, orbs, the map) still run theirs
 * first — this only fills the frames nobody else handles.
 */

/** A neutral tile: dark card-ish rectangle with a faint diagonal hatch. */
export const PLACEHOLDER_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" preserveAspectRatio="none">' +
  '<defs><pattern id="h" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">' +
  '<line x1="0" y1="0" x2="0" y2="8" stroke="#4a4a52" stroke-width="1"/></pattern></defs>' +
  '<rect width="64" height="64" fill="#2b2b31"/><rect width="64" height="64" fill="url(#h)"/>' +
  '<rect x="1" y="1" width="62" height="62" fill="none" stroke="#5a5a64" stroke-width="2"/></svg>';

export const PLACEHOLDER_DATA_URI = `data:image/svg+xml;utf8,${encodeURIComponent(PLACEHOLDER_SVG)}`;

const MARK = "artPlaceholder";

/**
 * Whether a failed image URL is game art (and not the app's own icons or a
 * data URI). Art is served from `<assetsBase>/…` — `/assets/...` locally and
 * in the static build, an absolute origin when offloaded.
 */
export function isArtUrl(src: string, assetsBase: string): boolean {
  if (!src || src.startsWith("data:")) return false;
  const base = assetsBase.replace(/\/$/, "");
  if (base.startsWith("http")) return src.startsWith(base + "/");
  try {
    return new URL(src, "http://x").pathname.startsWith(base + "/");
  } catch {
    return false;
  }
}

/** Swap `img` for the placeholder tile once; returns whether it did. */
export function applyPlaceholder(img: HTMLImageElement, assetsBase: string): boolean {
  if (img.dataset[MARK] === "1") return false;
  if (!isArtUrl(img.getAttribute("src") ?? "", assetsBase)) return false;
  img.dataset[MARK] = "1";
  img.src = PLACEHOLDER_DATA_URI;
  return true;
}

/**
 * Install the document-level listener. `error` events on `<img>` do not
 * bubble, so this must capture. Idempotent per document.
 */
export function installArtPlaceholder(doc: Document, assetsBase: () => string): void {
  const root = doc as Document & { __artPlaceholderInstalled?: boolean };
  if (root.__artPlaceholderInstalled) return;
  root.__artPlaceholderInstalled = true;
  doc.addEventListener(
    "error",
    (e) => {
      const t = e.target;
      if (t instanceof HTMLImageElement) applyPlaceholder(t, assetsBase());
    },
    true,
  );
}
