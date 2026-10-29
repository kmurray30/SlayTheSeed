import { useCallback, useState } from "react";

/**
 * Collapse + resize chrome shared by the workbench's two space-hungry docks:
 * the PPO policy panel under the board and the "Machine reasoning" region
 * above the run log.
 *
 * Both are worth having and both, given a shop screen with thirty options or a
 * wide MCTS search, grow until the thing they annotate — the game, the log —
 * is off screen. On a tablet in landscape that is the entire viewport. So each
 * gets the same two affordances: collapse to its header bar, and drag its free
 * edge to a height it then remembers.
 *
 * `localStorage` is absent under jsdom and THROWS in a browser with storage
 * blocked (private mode, third-party-cookie policies), so every access here is
 * guarded. A failure just means the preference does not survive the reload.
 */

function readPref(key: string): string | null {
  try {
    return globalThis.localStorage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function writePref(key: string, value: string): void {
  try {
    globalThis.localStorage?.setItem(key, value);
  } catch {
    // Preference not persisted; the panel still works this session.
  }
}

/** A persisted boolean panel preference (collapsed / not). */
export function usePanelFlag(key: string, fallback = false): [boolean, (v: boolean) => void] {
  const [on, setOn] = useState(() => {
    const raw = readPref(key);
    return raw == null ? fallback : raw === "1";
  });
  const set = useCallback((v: boolean) => {
    setOn(v);
    writePref(key, v ? "1" : "0");
  }, [key]);
  return [on, set];
}

/** A persisted string choice (e.g. the launcher's game version). Stored
 *  verbatim; the caller decides whether a remembered value is still valid. */
export function usePanelChoice(key: string, fallback: string): [string, (v: string) => void] {
  const [v, setV] = useState(() => readPref(key) || fallback);
  const set = useCallback((next: string) => {
    setV(next);
    writePref(key, next);
  }, [key]);
  return [v, set];
}

/** A persisted panel height in px, clamped to [`min`, `max`] on read and write
 *  so a stored value from a bigger screen (or a hand-edited one) can never
 *  wedge a panel open at 900px on a tablet. */
export function usePanelHeight(
  key: string, fallback: number, min: number, max: number,
): [number, (h: number) => void] {
  const [h, setH] = useState(() => {
    const n = Number(readPref(key));
    return Number.isFinite(n) && n > 0 ? clamp(n, min, max) : fallback;
  });
  const set = useCallback((v: number) => {
    const c = clamp(Math.round(v), min, max);
    setH(c);
    writePref(key, String(c));
  }, [key, min, max]);
  return [h, set];
}

export function clamp(n: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, n));
}
