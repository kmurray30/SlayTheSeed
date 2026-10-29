import { describe, it, expect } from "vitest";
import { panelVisible, reasoningView, type LastSearch } from "./searchPanel";
import type { Score } from "./api";

function score(over: Partial<Score> = {}): Score {
  return {
    stable_idx: 0, action: "Strike", win_prob: 0.5, q: 0, visits: 10,
    is_best: false, q_saturated: false, low_confidence: false, target_index: null, ...over,
  };
}

const search: LastSearch = {
  scores: [score({ action: "Strike", is_best: true }), score({ stable_idx: 1, action: "Defend" })],
  action: "Play Strike → Cultist",
  enemies: [{ name: "Cultist", hp: 12, max_hp: 48 } as unknown as LastSearch["enemies"][0]],
};

const base = {
  autoRunning: false, scores: [] as Score[], pv: null,
  lastSearch: null as LastSearch | null, hintBusy: false, autoHints: false,
};

describe("reasoningView", () => {
  // The bug this whole module exists for: while a run played itself out, the
  // panel rendered a flat "autoplaying…" and never the MCTS bars.
  it("shows the last search's bars while autoplaying", () => {
    const v = reasoningView({ ...base, autoRunning: true, lastSearch: search });
    expect(v).toMatchObject({ kind: "scores", scores: search.scores });
  });

  it("titles those bars with the move they chose, since they describe the PREVIOUS position", () => {
    const v = reasoningView({ ...base, autoRunning: true, lastSearch: search });
    expect(v.kind === "scores" && v.title).toBe("last search → Play Strike → Cultist");
  });

  it("keeps the last search on screen after the run is stopped", () => {
    const v = reasoningView({ ...base, autoRunning: false, lastSearch: search });
    expect(v).toMatchObject({ kind: "scores", scores: search.scores });
  });

  // Precedence matters: live scores are about the board you are looking at,
  // a recorded search is about the one before it. Getting this backwards
  // would label a hint you pressed H for with a stale move.
  it("prefers live scores over the last search", () => {
    const live = [score({ action: "Bash" })];
    const v = reasoningView({ ...base, scores: live, lastSearch: search });
    expect(v).toMatchObject({ kind: "scores", scores: live, title: null });
  });

  it("reports no pv for a recorded search (the step record carries none)", () => {
    const v = reasoningView({ ...base, pv: ["Strike", "Defend"], lastSearch: search });
    expect(v.kind === "scores" && v.pv).toBeNull();
  });

  it("says it is searching before the first step lands", () => {
    expect(reasoningView({ ...base, autoRunning: true })).toEqual({
      kind: "message", text: "searching…",
    });
  });

  it("ignores an empty search rather than rendering a bar-less panel", () => {
    const v = reasoningView({
      ...base, autoRunning: true, lastSearch: { scores: [], action: "Choose path", enemies: [] },
    });
    expect(v).toEqual({ kind: "message", text: "searching…" });
  });

  // The board moves on between the search and the render. Labelling a row's
  // target against the live enemy list renames it the moment something dies.
  it("carries the board the search saw, and defers to the live one otherwise", () => {
    const recorded = reasoningView({ ...base, lastSearch: search });
    expect(recorded.kind === "scores" && recorded.enemies).toEqual(search.enemies);
    const liveNow = reasoningView({ ...base, scores: [score()], lastSearch: search });
    expect(liveNow.kind === "scores" && liveNow.enemies).toBeNull();
  });

  it("prompts for a hint when idle and nothing is pending", () => {
    expect(reasoningView(base)).toEqual({ kind: "message", text: "press H for a hint" });
  });

  it("says thinking while a hint is in flight", () => {
    expect(reasoningView({ ...base, hintBusy: true })).toEqual({ kind: "message", text: "thinking…" });
  });
});

describe("panelVisible", () => {
  const off = {
    hintsOn: false, hintShown: false, autoRunning: false, autoPaused: false,
    lastSearch: null as LastSearch | null,
  };

  // The opt-in rule: unassisted by default.
  it("stays hidden while you play unassisted", () => {
    expect(panelVisible(off)).toBe(false);
  });

  it("opens when you press H, switch or no switch", () => {
    expect(panelVisible({ ...off, hintShown: true })).toBe(true);
  });

  it("opens for the whole-run automation, which is exempt from the opt-in", () => {
    expect(panelVisible({ ...off, autoRunning: true })).toBe(true);
  });

  it("stays open across a Stop so you can read why the run is going badly", () => {
    expect(panelVisible({ ...off, autoPaused: true, lastSearch: search })).toBe(true);
  });

  // Taking the run back is taking the advice away — otherwise a stopped run
  // would leave the panel armed over moves you make yourself.
  it("closes again once you take a move yourself", () => {
    expect(panelVisible({ ...off, autoPaused: true, lastSearch: null })).toBe(false);
  });
});
