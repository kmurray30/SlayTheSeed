import { describe, it, expect } from "vitest";
import { activeNode, readableRows } from "./combatlog";
import type { CombatLogRow, LiveNode, LiveTimeline } from "./api";

function row(text: string): CombatLogRow {
  return {
    kind: "damage", round: 1, side: "player", text, verb: "dealt",
    actor: "The Ironclad", actor_side: "player", target: "Nibbit", target_side: "enemy",
    amount: 6, noun: null, source: "Strike",
  };
}

/** A row of a kind the game's log never prints — see `PANEL_HIDDEN_KINDS`. */
function hidden(kind: string, text: string): CombatLogRow {
  return { ...row(text), kind };
}

function node(over: Partial<LiveNode> & { id: number }): LiveNode {
  return {
    parent_id: over.id === 0 ? null : over.id - 1,
    idx: over.id, decision: "combat_play", label: "", details: [],
    error: false, child_ids: [], floor: 1, act: 1, ...over,
  };
}

function timeline(nodes: LiveNode[]): LiveTimeline {
  return {
    nodes,
    active_path: nodes.map((n) => n.id),
    active_leaf: nodes[nodes.length - 1]?.id ?? null,
  };
}

describe("the reader / VFX split", () => {
  // A timeline node carries the view-only `power_flash` rows (branching.py
  // renders its slices with `keep={"power_flash"}`) because the board's effect
  // stream needs them. These two tests pin the split as deliberate: the
  // ledgers' filter drops them, the VFX path's accessor keeps them.
  const tl = () =>
    timeline([
      node({ id: 0, combat_log: [row("hit"), hidden("power_flash", "Nibbit's Thorns triggered.")] }),
    ]);

  it("keeps view-only rows OUT of the rows a reader is shown", () => {
    const rows = tl().nodes.flatMap((n) => n.combat_log ?? []);
    expect(readableRows(rows).map((r) => r.kind)).toEqual(["damage"]);
  });

  it("keeps view-only rows IN the active node's rows, where the VFX layer reads them", () => {
    expect(activeNode(tl())?.combat_log?.map((r) => r.kind)).toEqual(["damage", "power_flash"]);
  });

});

describe("activeNode", () => {
  it("is null without a timeline", () => {
    expect(activeNode(null)).toBeNull();
  });

  it("returns the node the active path ends on, with ONLY its own slice", () => {
    // The distinction that matters for the board's per-hit stream: this is one
    // action's rows, not the fight's.
    const tl = timeline([
      node({ id: 0, combat_log: [row("first")] }),
      node({ id: 1, combat_log: [row("second")] }),
    ]);
    expect(activeNode(tl)?.id).toBe(1);
    expect(activeNode(tl)?.combat_log?.map((r) => r.text)).toEqual(["second"]);
  });

  it("follows a seek back rather than the newest node", () => {
    const nodes = [node({ id: 0 }), node({ id: 1 }), node({ id: 2 })];
    const tl: LiveTimeline = { nodes, active_path: [0, 1], active_leaf: 1 };
    expect(activeNode(tl)?.id).toBe(1);
  });
});
