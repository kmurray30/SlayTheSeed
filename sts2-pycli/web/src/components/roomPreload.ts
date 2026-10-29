/**
 * Hold a fight off-screen until every piece of its art has decoded.
 *
 * A room is not one image, it is a stack of 5 to 68 layers (`roomScenes.ts`) —
 * 37 distinct PNGs in the plain Overgrowth room alone — each of them its own
 * element with its own `background-image`. The browser fetches those
 * independently and paints each one the instant it arrives, so a cold cache
 * draws the room the way a slide projector would: the far wall, then the
 * canopy, then the floor, then the props — with the fighters already standing
 * on the gradient in front of a half-built room. And the fighters are no
 * better: a Spine rig is a `.skel`, an `.atlas` and the atlas's page images,
 * fetched one after the other, and every card in the opening hand is its own
 * portrait — so a new fight used to assemble itself in front of you:
 * background, then the enemy, then the cards.
 *
 * So the scene does not go up a piece at a time: it goes up whole. This module
 * loads a scene's art into the browser cache — room layers, rig files (and the
 * pages the atlas names), card portraits — and `useArtReady` tells the Board
 * when all of it is there; until then the stage shows its own fallback
 * gradient (`.gs-arena-stage`) with the actors and the hand invisible, and the
 * whole fight fades in as one piece. The gate is keyed to the FIGHT (see
 * `useArtReady`'s `key`): a card drawn mid-fight pops in as before rather than
 * hiding a scene that is already up.
 *
 * The cache is module-level and never evicted: a scene is a few MB the browser
 * is already holding, and remembering that we have it is what makes a
 * re-render, a step back and forth, or a return to a room already seen paint
 * instantly instead of re-gating.
 */
import { useEffect, useState } from "react";
import type { RoomLayer } from "./roomScenes";

/** URLs whose art has finished loading (or failed — see `settle`). */
const decoded = new Set<string>();
/** Loads already in flight, so a second render does not re-issue them. */
const inflight = new Map<string, Promise<void>>();

/** How long the scene may be held back before it is shown regardless.
 *
 *  A stalled fetch or an art file the deploy is missing must never leave the
 *  fight on a bare gradient forever: past this the scene draws as it always
 *  did, piece by piece. Long enough that a slow-but-working scene still
 *  arrives whole, short enough that a broken one is not a blank fight. */
const MAX_HOLD_MS = 4000;

/** The art URLs one resolved stack paints, deduped and in draw order.
 *
 *  Variants of a slot share art freely (Glory's three ruin variants all hang
 *  the same torch), so the same file turns up more than once in a stack. */
export function roomArtUrls(url: (rel: string) => string, ...lists: RoomLayer[][]): string[] {
  const seen = new Set<string>();
  for (const list of lists) for (const layer of list) seen.add(url(`rooms/${layer.src}`));
  return [...seen];
}

/** The files a Spine rig loads, given its base URL (what `SpineSprite` gets).
 *
 *  The atlas's page images are not listed here — their names live inside the
 *  atlas — `preloadArt` reads the atlas and loads them as part of it. */
export function rigArtUrls(rigBase: string | null | undefined): string[] {
  if (!rigBase) return [];
  return [`${rigBase}.skel`, `${rigBase}.atlas`];
}

/** The page image names an atlas declares: the lines that name an image file
 *  (`axebot.png`), each followed by its `size:` block. Empty for a file that
 *  is not an atlas at all. */
export function atlasPages(text: string): string[] {
  const pages: string[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (/\.(png|webp|jpg|jpeg)$/i.test(line) && !line.includes(":")) pages.push(line);
  }
  return pages;
}

/** Every URL for one fight's art: the room stack, every rig, every card in
 *  hand. Deduped, in that order; nulls skipped. */
export function sceneArtUrls(
  roomUrls: readonly string[],
  rigBases: readonly (string | null | undefined)[],
  cardImages: readonly (string | null | undefined)[],
): string[] {
  const seen = new Set<string>(roomUrls);
  for (const rig of rigBases) for (const u of rigArtUrls(rig)) seen.add(u);
  for (const img of cardImages) if (img) seen.add(img);
  return [...seen];
}

/** Start (or join) the load of every URL; resolves once they have all settled. */
export function preloadArt(urls: readonly string[]): Promise<void> {
  return Promise.all(urls.map(load)).then(() => undefined);
}
/** @deprecated name kept for the room-only callers; same as `preloadArt`. */
export const preloadRoomArt = preloadArt;

/** Is every one of these already in the browser, as far as we know? */
export function artReady(urls: readonly string[]): boolean {
  for (const u of urls) if (!decoded.has(u)) return false;
  return true;
}
/** @deprecated name kept for the room-only callers; same as `artReady`. */
export const roomArtReady = artReady;

function load(url: string): Promise<void> {
  if (decoded.has(url)) return Promise.resolve();
  const existing = inflight.get(url);
  if (existing) return existing;
  const started = /\.skel$/i.test(url)
    ? loadFile(url).then(() => undefined)
    : /\.atlas$/i.test(url)
      ? loadAtlas(url)
      : loadImage(url);
  const settled = started.then(() => {
    decoded.add(url);
    inflight.delete(url);
  });
  inflight.set(url, settled);
  return settled;
}

/** Fetch a non-image file into the browser cache; a failure is settled, not
 *  pending (see `loadImage`). Resolves with the text when it was readable. */
async function loadFile(url: string): Promise<string | null> {
  if (typeof fetch !== "function") return null;
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

/** The atlas, then every page image it names (or the rig's own `.png` when
 *  the atlas could not be read — the one page nearly every rig has). */
async function loadAtlas(url: string): Promise<void> {
  const text = await loadFile(url);
  const dir = url.slice(0, url.lastIndexOf("/") + 1);
  const pages = text ? atlasPages(text) : [];
  const urls = pages.length ? pages.map((p) => dir + p) : [url.replace(/\.atlas$/i, ".png")];
  await Promise.all(urls.map(load));
}

function loadImage(url: string): Promise<void> {
  return new Promise<void>((resolve) => {
    const img = new Image();
    // A 404 counts as settled, not as pending. The scene is held back for the
    // art it is GOING to get; art it will never get renders as nothing either
    // way, and waiting on it only costs the whole fight.
    const settle = () => resolve();
    img.onload = settle;
    img.onerror = settle;
    // NO `crossOrigin`: the layers paint through `background-image`, which is a
    // no-CORS request. Asking for CORS here would fetch the file into a
    // DIFFERENT cache entry than the one the layer then asks for, and the
    // preload would warm nothing while doubling the bytes. (The art is served
    // from a GCS origin in lean deploys — see `assetUrl` — so this is a real
    // cross-origin case, not a hypothetical one.)
    img.src = url;
    // `decode()` waits for the pixels to be ready to paint, not merely
    // downloaded, which is the difference between "the layer arrives" and "the
    // layer arrives without a hitch". Where it is missing (jsdom, older
    // Safari), the load events above are the fallback.
    if (typeof img.decode === "function") img.decode().then(settle, settle);
    else if (img.complete) settle();
  });
}

/** Whether this art may be painted yet.
 *
 *  `key` names WHAT is being gated. The URL list is captured when the key
 *  changes and kept until it changes again, so a list that shifts under a
 *  stable key (the hand after a draw) neither re-gates nor re-hides what is
 *  already on screen. Without a key, the list is its own key (a room stack).
 *
 *  Art that is all cached is ready on the FIRST render — the check is made
 *  during render, not in an effect. An effect runs after the browser has
 *  already painted, which is one frame of exactly the half-drawn scene this
 *  exists to prevent, and re-entering a fight already seen is the common case
 *  in a workbench you step back and forth through. */
export function useArtReady(urls: readonly string[], key: string = urls.join("|")): boolean {
  const [state, setState] = useState(() => ({ key, urls: [...urls], ready: artReady(urls) }));
  // The key changed under us (a new act, a boss's own room, the next fight):
  // re-gate now, in render, rather than a frame later. React re-runs this
  // component immediately and discards the paint, so nothing reaches the
  // screen between.
  if (state.key !== key) setState({ key, urls: [...urls], ready: artReady(urls) });
  const ready = state.key === key && state.ready;
  const gated = state.key === key ? state.urls : urls;

  useEffect(() => {
    if (ready) return;
    let live = true;
    const open = () => {
      if (live) setState((prev) => (prev.key === key ? { ...prev, ready: true } : prev));
    };
    // The hold is a deadline, not a promise: `preloadArt` settles on a 404
    // but not on a fetch that simply never answers.
    const timer = setTimeout(open, MAX_HOLD_MS);
    void preloadArt(gated).then(open);
    return () => { live = false; clearTimeout(timer); };
    // `gated` is captured with the key, so the key is the honest dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, ready]);

  return ready;
}
/** @deprecated name kept for the room-only callers; same as `useArtReady`. */
export const useRoomArtReady = useArtReady;
