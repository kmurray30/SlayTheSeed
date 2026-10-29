import { describe, it, expect } from "vitest";
import { liveSeekTargetForIdx, liveSeekTargetForDecision } from "./liveSeek";
import type { DecisionRegret, LiveTimeline, LiveNode } from "./api";

function node(over: Partial<LiveNode> & Pick<LiveNode, "id" | "idx">): LiveNode {
  return {
    parent_id: null,
    decision: "combat_play",
    label: "",
    details: [],
    error: false,
    child_ids: [],
    ...over,
  };
}

function timeline(over: Partial<LiveTimeline> = {}): LiveTimeline {
  // A forked tree: root(0) → A(1) → B(2) is the active line; C(1) is a
  // pruned sibling sharing depth 1 with A.
  const nodes: LiveNode[] = [
    node({ id: 10, idx: 0, child_ids: [11, 99] }),
    node({ id: 11, idx: 1, parent_id: 10, child_ids: [12] }),
    node({ id: 12, idx: 2, parent_id: 11 }),
    node({ id: 99, idx: 1, parent_id: 10 }),
  ];
  return { nodes, active_path: [10, 11, 12], active_leaf: 12, ...over };
}

describe("liveSeekTargetForIdx", () => {
  it("maps a decision depth to the active-path node id at that depth", () => {
    expect(liveSeekTargetForIdx(timeline(), 0)).toBe(10);
    expect(liveSeekTargetForIdx(timeline(), 1)).toBe(11);
    expect(liveSeekTargetForIdx(timeline(), 2)).toBe(12);
  });

  it("ignores off-path nodes that share a depth (forked siblings)", () => {
    // Depth 1 exists on both node 11 (active) and node 99 (pruned); only the
    // active-path one is a valid seek target — never the sibling.
    expect(liveSeekTargetForIdx(timeline(), 1)).toBe(11);
  });

  it("returns null when no active-path node has that depth", () => {
    expect(liveSeekTargetForIdx(timeline(), 5)).toBeNull();
  });

  it("returns null with no live timeline", () => {
    expect(liveSeekTargetForIdx(null, 0)).toBeNull();
  });
});

function dec(over: Partial<DecisionRegret> & Pick<DecisionRegret, "idx">): DecisionRegret {
  return {
    turn: 1, floor: 1, act: 1, played_label: "", played_win_prob: 0, best_label: "",
    best_win_prob: 0, regret: 0, drop_pct: 0, player_rank: 0, n_actions: 2, visits: 0,
    matched_best: false, is_blunder: true, message: "",
    played_mean_hp: null, best_mean_hp: null, hp_drop: null, severity: 0,
    verdict: "blunder", best_line: [], ...over,
  };
}

describe("liveSeekTargetForDecision", () => {
  it("prefers the recorded node_id, even when that node is OFF the active path", () => {
    // The bug: after seeking back, the active path truncates, so a later
    // decision's node falls onto a pruned sibling branch. Resolving by depth
    // (liveSeekTargetForIdx) returns null and the click is a silent no-op;
    // resolving by node_id still finds the exact node.
    const t = timeline({ active_path: [10, 11], active_leaf: 11 }); // rewound to depth 1
    expect(liveSeekTargetForIdx(t, 2)).toBeNull(); // depth-2 node 12 is now off-path
    expect(liveSeekTargetForDecision(t, dec({ idx: 2, node_id: 12 }))).toBe(12);
  });

  it("falls back to depth-on-active-path when node_id is absent (older payloads)", () => {
    const t = timeline();
    expect(liveSeekTargetForDecision(t, dec({ idx: 1 }))).toBe(11);
    expect(liveSeekTargetForDecision(t, dec({ idx: 1, node_id: null }))).toBe(11);
  });
});
