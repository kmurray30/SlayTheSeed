import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import SearchTree from "../SearchTree";
import { DEFAULT_MCTS_CONFIG, type MctsConfig, type SearchTree as Tree, type SearchTreeNode } from "../../api";

const cfg = (sims: number): MctsConfig => ({ ...DEFAULT_MCTS_CONFIG, sims });

function n(over: Partial<SearchTreeNode> & { id: number }): SearchTreeNode {
  return {
    parent: null, visits: 0, q: 0, value_sum: 0, wins: 0, losses: 0, ties: 0,
    is_terminal: false, proven_win: false, proven_loss: false, children: [],
    kind: null, label: null, target_index: null, ...over,
  };
}

// root(0) → Strike(1, deep child 3) and Defend(2).
const tree: Tree = {
  nodes: [
    n({ id: 0, visits: 300, q: 0.6, children: [1, 2] }),
    n({ id: 1, parent: 0, visits: 250, q: 0.72, wins: 180, ties: 70, label: "Strike", kind: "play", target_index: 0, children: [3] }),
    n({ id: 2, parent: 0, visits: 50, q: 0.4, label: "Defend", kind: "play", children: [] }),
    n({ id: 3, parent: 1, visits: 120, q: 0.8, label: "End turn", kind: "end_turn", children: [] }),
  ],
  root: 0,
  pv: [],
  sims: 300,
  c_puct: 1.41,
};

describe("SearchTree (outline view, default)", () => {
  it("renders the root expanded with its children; grandchild stays hidden", () => {
    render(<SearchTree tree={tree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    expect(screen.getByText("Strike")).toBeInTheDocument();
    expect(screen.getByText("Defend")).toBeInTheDocument();
    expect(screen.queryByText("End turn")).toBeNull(); // parent not expanded yet
  });

  it("expands a row on click to reveal its children", () => {
    render(<SearchTree tree={tree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Strike"));
    expect(screen.getByText("End turn")).toBeInTheDocument();
  });

  it("shows the selected row's stats in the detail strip", () => {
    render(<SearchTree tree={tree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Strike"));
    expect(screen.getByText(/W\/T\/L 180\/70\/0/)).toBeInTheDocument();
  });

  it("expand-all reveals deep nodes; collapse returns to the root's children", () => {
    render(<SearchTree tree={tree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    expect(screen.queryByText("End turn")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /expand all/ }));
    expect(screen.getByText("End turn")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^collapse$/ }));
    expect(screen.queryByText("End turn")).toBeNull();
  });

  it("'best +10' unfolds only the most-visited line, not sibling branches", () => {
    const t: Tree = {
      nodes: [
        n({ id: 0, visits: 250, children: [1, 2] }),
        n({ id: 1, parent: 0, visits: 200, label: "Strike", children: [3] }),
        n({ id: 2, parent: 0, visits: 50, label: "Defend", children: [4] }),
        n({ id: 3, parent: 1, visits: 150, label: "End turn", children: [] }),
        n({ id: 4, parent: 2, visits: 30, label: "Bash", children: [] }),
      ],
      root: 0, pv: [], sims: 250,
    };
    render(<SearchTree tree={t} config={cfg(250)} onRun={() => {}} onClose={() => {}} />);
    expect(screen.queryByText("End turn")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /best \+10/ }));
    expect(screen.getByText("End turn")).toBeInTheDocument(); // best line (Strike→End turn) opened
    expect(screen.queryByText("Bash")).toBeNull(); // Defend's branch stays folded
  });

  it("resolves a potion slot edge to the actual potion name", () => {
    const t: Tree = {
      nodes: [
        n({ id: 0, visits: 100, children: [1] }),
        n({ id: 1, parent: 0, visits: 40, label: "Potion s0", kind: "potion", children: [] }),
      ],
      root: 0, pv: [], sims: 100,
    };
    render(<SearchTree tree={t} config={cfg(100)} onRun={() => {}} onClose={() => {}}
      potionNames={["Fire Potion"]} />);
    expect(screen.getByText("Fire Potion")).toBeInTheDocument();
    expect(screen.queryByText("Potion s0")).toBeNull();
  });

  it("shows the per-node outcome summary in the detail strip", () => {
    const sm = (hp: number, energy: number, ehp: number) => ({
      player_hp: hp, player_max_hp: 80, player_block: 0, energy, round: 1,
      enemies: [{ uid: 2, hp: ehp, max_hp: 56 }],
    });
    const t: Tree = {
      nodes: [
        n({ id: 0, visits: 100, children: [1], summary: sm(80, 3, 56) }),
        n({ id: 1, parent: 0, visits: 60, label: "Strike", kind: "play", summary: sm(80, 2, 50) }),
      ],
      root: 0, pv: [], sims: 100,
    };
    render(<SearchTree tree={t} config={cfg(100)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Strike"));
    expect(screen.getByText("energy 2")).toBeInTheDocument();
    expect(screen.getByText(/enemies 50\/56/)).toBeInTheDocument();
  });

  it("labels the outline columns and explains stats on hover", () => {
    const sm = { player_hp: 80, player_max_hp: 80, player_block: 0, energy: 2, round: 1, enemies: [{ uid: 2, hp: 50, max_hp: 56 }] };
    const t: Tree = {
      nodes: [
        n({ id: 0, visits: 100, children: [1], summary: { ...sm, energy: 3, enemies: [{ uid: 2, hp: 56, max_hp: 56 }] } }),
        n({ id: 1, parent: 0, visits: 60, label: "Strike", kind: "play", summary: sm }),
      ],
      root: 0, pv: [], sims: 100,
    };
    render(<SearchTree tree={t} config={cfg(100)} onRun={() => {}} onClose={() => {}} />);
    // Column header present (clarifies "what is the last number").
    expect(screen.getByText("outcome")).toBeInTheDocument();
    // Hovering a row shows a human-readable card.
    fireEvent.mouseEnter(screen.getByText("Strike"));
    expect(screen.getByText("Visits")).toBeInTheDocument();
    expect(screen.getByText("Player HP")).toBeInTheDocument();
    expect(screen.getByText("Enemy 0 HP")).toBeInTheDocument();
  });

  it("runs the search with edited MCTS knobs only when applied", () => {
    const runs: MctsConfig[] = [];
    render(<SearchTree tree={tree} config={cfg(300)} onRun={(c) => runs.push(c)} onClose={() => {}} />);
    // Editing a knob does not re-run on its own…
    fireEvent.change(screen.getByLabelText(/explore/i, { selector: "input" }), { target: { value: "2.5" } });
    expect(runs).toHaveLength(0);
    // …the button turns "run ▶" (dirty) and applies the whole config on click.
    fireEvent.click(screen.getByRole("button", { name: /run/ }));
    expect(runs).toHaveLength(1);
    expect(runs[0].c_puct).toBe(2.5);
    expect(runs[0].sims).toBe(300);
  });

  it("re-rolls the seed via the dice button", () => {
    render(<SearchTree tree={tree} config={{ ...cfg(300), seed: 4 }} onRun={() => {}} onClose={() => {}} />);
    const seed = screen.getByLabelText(/seed/i, { selector: "input" }) as HTMLInputElement;
    expect(seed.value).toBe("4");
    fireEvent.click(screen.getByRole("button", { name: "🎲" }));
    expect(seed.value).toBe("5");
  });

  it("surfaces a backend error", () => {
    render(<SearchTree tree={{ nodes: [], root: null, pv: [], error: "boom" }} config={cfg(300)}
      onRun={() => {}} onClose={() => {}} />);
    expect(screen.getByText(/boom/)).toBeInTheDocument();
  });
});

// root(0 decision) → chance node 1 (action BattleTrance) whose children are
// sampled outcomes 3 (modal, +0.65) and 4 (−0.20); plus a plain Defend(2).
const chanceTree: Tree = {
  nodes: [
    n({ id: 0, visits: 300, q: 0.5, node_kind: "decision", children: [1, 2] }),
    n({
      id: 1, parent: 0, visits: 200, q: 0.4, label: "BattleTrance", kind: "play",
      node_kind: "chance", chance_action: "BattleTrance", outcome_cap: 3,
      chance_draws: 5, chance_closed: false, children: [3, 4],
    }),
    n({ id: 2, parent: 0, visits: 100, q: 0.3, label: "Defend", kind: "play", node_kind: "decision", children: [] }),
    n({ id: 3, parent: 1, visits: 150, q: 0.65, kind: "outcome", node_kind: "decision", outcome_ordinal: 0, children: [5] }),
    n({ id: 4, parent: 1, visits: 50, q: -0.2, kind: "outcome", node_kind: "decision", outcome_ordinal: 1, children: [] }),
    n({ id: 5, parent: 3, visits: 120, q: 0.7, label: "End turn", kind: "end_turn", node_kind: "decision", children: [] }),
  ],
  root: 0, pv: [], sims: 300, c_puct: 1.41,
};

describe("SearchTree (chance nodes)", () => {
  it("renders a chance node with a 🎲 glyph and its action, and an E[V] not Q", () => {
    render(<SearchTree tree={chanceTree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    // The chance row shows the action name, a dice glyph and the DPW k/cap hint.
    const row = screen.getByText("BattleTrance").parentElement!;
    expect(row.textContent).toContain("🎲");
    expect(row.textContent).toContain("2/3"); // k=2 sampled of cap=3
    fireEvent.click(screen.getByText("BattleTrance"));
    // Detail strip: expectimax outcome badge + min…max spread (ASCII minus).
    expect(screen.getByText(/2\/3 outcomes/)).toBeInTheDocument();
    expect(screen.getByText(/spread -0\.20 … \+0\.65/)).toBeInTheDocument();
  });

  it("shows outcome children as probability rows (p = visits / chance visits)", () => {
    render(<SearchTree tree={chanceTree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("BattleTrance")); // expand the chance node
    // Outcome 3 = 150/200 = .75, outcome 4 = 50/200 = .25.
    expect(screen.getByText(/p=\.75/)).toBeInTheDocument();
    expect(screen.getByText(/p=\.25/)).toBeInTheDocument();
  });

  it("'collapse dice' folds the chance layer to the modal outcome's children", () => {
    render(<SearchTree tree={chanceTree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    // Default (full depth): expanding the chance node shows outcome p-rows.
    fireEvent.click(screen.getByText("BattleTrance"));
    expect(screen.getByText(/p=\.75/)).toBeInTheDocument();
    expect(screen.queryByText("End turn")).toBeNull();
    // Toggle collapse: the chance row (already expanded) now connects straight
    // to the modal outcome's child (End turn); the outcome p-rows disappear.
    fireEvent.click(screen.getByRole("button", { name: /collapse dice/ }));
    expect(screen.getByText("End turn")).toBeInTheDocument();
    expect(screen.queryByText(/p=\.75/)).toBeNull();
    // Still honestly labelled with the chance action (outline row + detail).
    expect(screen.getAllByText("BattleTrance").length).toBeGreaterThanOrEqual(1);
  });


  it("graph view draws chance nodes as diamonds (rect) and decisions as circles", () => {
    const { container } = render(<SearchTree tree={chanceTree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "graph" }));
    // Decision root is a circle; the chance node is a rotated rect (diamond).
    expect(container.querySelector("circle[aria-label='root']")).toBeTruthy();
    expect(container.querySelector("rect[aria-label='BattleTrance']")).toBeTruthy();
  });
});

describe("SearchTree (graph view)", () => {
  function toGraph() {
    render(<SearchTree tree={tree} config={cfg(300)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "graph" }));
  }

  it("renders nodes as circles and expands on click", () => {
    toGraph();
    expect(screen.getByLabelText("root")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Strike"));
    expect(screen.getByText("End turn")).toBeInTheDocument();
  });

  it("zooms in/out and resets to fit", () => {
    toGraph();
    expect(screen.getByText("100%")).toBeInTheDocument(); // default fit (jsdom: 1:1)
    fireEvent.click(screen.getByLabelText("zoom in"));
    expect(screen.getByText("125%")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^fit$/ }));
    expect(screen.getByText("100%")).toBeInTheDocument();
  });
});

// The search graph is a DAG: Zap→BeamCell and BeamCell→Zap reach ONE node (3),
// allocated under BeamCell (so its own label is "Zap"). Each appearance must be
// labelled and counted by its own edge, and expand independently.
const dag: Tree = {
  nodes: [
    n({ id: 0, visits: 100, children: [1, 2],
      edges: [
        { child: 1, kind: "play", label: "Zap", target_index: null, visits: 60 },
        { child: 2, kind: "play", label: "BeamCell", target_index: 0, visits: 40 },
      ] }),
    n({ id: 1, parent: 0, visits: 60, label: "Zap", kind: "play", children: [3],
      edges: [{ child: 3, kind: "play", label: "BeamCell", target_index: 0, visits: 20 }] }),
    n({ id: 2, parent: 0, visits: 40, label: "BeamCell", kind: "play", target_index: 0, children: [3],
      edges: [{ child: 3, kind: "play", label: "Zap", target_index: null, visits: 30 }] }),
    n({ id: 3, parent: 2, visits: 50, wins: 50, label: "Zap", kind: "play", children: [4],
      edges: [{ child: 4, kind: "end_turn", label: "End turn", target_index: null, visits: 45 }] }),
    n({ id: 4, parent: 3, visits: 45, label: "End turn", kind: "end_turn", children: [] }),
  ],
  root: 0, pv: [], sims: 100, c_puct: 2.5,
};

describe("SearchTree (transposed DAG)", () => {
  it("labels a shared node by the edge that reaches it, with that edge's visits", () => {
    render(<SearchTree tree={dag} config={cfg(100)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Zap"));
    // Under Zap the shared node reads "BeamCell" (not its own "Zap" label)...
    expect(screen.getAllByText("BeamCell")).toHaveLength(2);
    // ...counted by Zap's traffic through that edge, flagged as shared.
    expect(screen.getByText("N 20*")).toBeInTheDocument();
  });

  it("expands each appearance of a shared node independently", () => {
    render(<SearchTree tree={dag} config={cfg(100)} onRun={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Zap"));
    // Rows are DFS-ordered: root, Zap, its BeamCell (the shared node), BeamCell.
    fireEvent.click(screen.getAllByText("BeamCell")[0]);
    expect(screen.getAllByText("End turn")).toHaveLength(1);
    // Opening the root's BeamCell reveals the shared node again — as "Zap",
    // with BeamCell's traffic — still folded: its subtree is not shown twice.
    fireEvent.click(screen.getAllByText("BeamCell")[1]);
    expect(screen.getAllByText("Zap")).toHaveLength(2);
    expect(screen.getByText("N 30*")).toBeInTheDocument();
    expect(screen.getAllByText("End turn")).toHaveLength(1);
  });
});
