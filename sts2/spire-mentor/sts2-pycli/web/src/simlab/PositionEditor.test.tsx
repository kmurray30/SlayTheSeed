import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import PositionEditor from "./PositionEditor";
import { CATALOG, samplePosition } from "./testMocks";
import type { Position } from "./position";

function last(fn: { mock: { calls: [Position][] } }): Position {
  return fn.mock.calls[fn.mock.calls.length - 1][0];
}

function mount(pos: Position = samplePosition()) {
  const onChange = vi.fn<(p: Position) => void>();
  const onNew = vi.fn();
  const onImportText = vi.fn();
  render(<PositionEditor position={pos} catalog={CATALOG} onChange={onChange} onNew={onNew} onImportText={onImportText} />);
  return { onChange, onNew, onImportText };
}

describe("PositionEditor", () => {
  it("shows the hand with display names, grouped counts and the pile total", () => {
    mount();
    expect(screen.getByText("6 in all four piles")).toBeInTheDocument();
    expect(screen.getByLabelText("hand card 1")).toHaveValue("STRIKE_IRONCLAD");
    expect(screen.getByLabelText("hand card 1 count")).toHaveValue(2);
    expect(screen.getAllByText("Strike").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Fuzzy Wurm Crawler").length).toBeGreaterThan(0);
  });

  it("adds a card to the hand", () => {
    const { onChange } = mount();
    fireEvent.click(screen.getByLabelText("add card to hand"));
    const next = last(onChange);
    expect(next.piles.hand).toHaveLength(5);
    expect(next.piles.hand[next.piles.hand.length - 1]).toEqual({ card: "", upgrade: 0, cost: null });
  });

  it("moves a card to the draw pile through the pile select", () => {
    const { onChange } = mount();
    fireEvent.change(screen.getByLabelText("hand card 4 pile"), { target: { value: "draw" } });
    const next = last(onChange);
    expect(next.piles.hand.map((c: { card: string }) => c.card)).toEqual(["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "DEFEND_IRONCLAD"]);
    expect(next.piles.draw[next.piles.draw.length - 1].card).toBe("BASH");
  });

  it("edits player HP and an enemy's intent", () => {
    const { onChange } = mount();
    fireEvent.change(screen.getByLabelText("player hp"), { target: { value: "40" } });
    expect(last(onChange).player.hp).toBe(40);
    fireEvent.change(screen.getByLabelText("enemy 1 intent"), { target: { value: "INHALE" } });
    expect(last(onChange).monsters[0].intent).toBe("INHALE");
    const intent = screen.getByLabelText("enemy 1 intent") as HTMLSelectElement;
    expect(within(intent).getByText(/Inhale/)).toBeInTheDocument();
  });

  it("removes an enemy", () => {
    const { onChange } = mount();
    fireEvent.click(screen.getByLabelText("remove enemy 1"));
    expect(last(onChange).monsters).toEqual([]);
  });

  it("asks for a fresh fight from the setup row", () => {
    const { onNew } = mount();
    fireEvent.click(screen.getByText("starting hand"));
    expect(onNew).toHaveBeenCalledWith({ character: "Ironclad", encounter: "FUZZY_WURM_CRAWLER_WEAK", ascension: 0, seed: 1 });
    fireEvent.change(screen.getByLabelText("new position for"), { target: { value: "VANTOM_BOSS" } });
    expect(onNew.mock.calls[onNew.mock.calls.length - 1][0].encounter).toBe("VANTOM_BOSS");
  });

  it("applies pasted position JSON and hands other pastes to the server", () => {
    const { onChange, onImportText } = mount();
    const box = screen.getByLabelText("position json");
    const edited = { ...samplePosition(), turn: 3 };
    fireEvent.change(box, { target: { value: JSON.stringify(edited) } });
    fireEvent.click(screen.getByRole("button", { name: "apply" }));
    expect(last(onChange).turn).toBe(3);
    fireEvent.change(box, { target: { value: '{"type":"advice_request","advice_for":"combat_action","hand":[]}' } });
    fireEvent.click(screen.getByRole("button", { name: "apply" }));
    expect(onImportText).toHaveBeenCalledWith('{"type":"advice_request","advice_for":"combat_action","hand":[]}');
  });

  it("reverts unapplied JSON edits", () => {
    const { onChange } = mount();
    const box = screen.getByLabelText("position json") as HTMLTextAreaElement;
    fireEvent.change(box, { target: { value: "garbage" } });
    fireEvent.click(screen.getByRole("button", { name: "revert" }));
    expect(box.value).toContain('"schema": "simlab-position/1"');
    expect(onChange).not.toHaveBeenCalled();
  });
});
