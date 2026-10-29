import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import type { Step } from "../api";
import Shop from "./Shop";
import { SoundFixture } from "../sound/SoundContext";
import type { SfxMap } from "../sound/sfxMap";

function step(decision: string, state: Record<string, unknown>): Step {
  return {
    idx: 0, decision, state: { decision, ...state } as Step["state"],
    scores: [], pv: null, chosen_action: null, source: "live", meta: { turn: null, step_no: 0 },
  };
}

const s = step("shop", {
  player: { name: "Ironclad", gold: 100 },
  cards: [],
  relics: [
    { index: 0, name: "Akabeko", cost: 75, is_stocked: true },
    { index: 1, name: "Anchor", cost: 50, is_stocked: true },
  ],
  potions: [],
});

describe("Shop — relic purchase cue", () => {
  // Renders Shop inside a SoundFixture whose `emit` is a real vi.fn(), so
  // this can only pass if Shop.tsx actually calls sound.emit(cueForRelic(...))
  // from the relic buy button — not just that cueForRelic itself resolves
  // correctly (that's runCues.test.ts's job).
  // Mutation caught: deleting the `sound.emit(...)` block from buyRelic, or
  // reverting `buyRelic` back to the bare `buy(...)` call.
  it("emits the relic's own sound when bought", () => {
    const emit = vi.fn();
    const map: SfxMap = {
      version: "test", generic: {}, cards: {}, powers: {}, monsters: {},
      relics: { Akabeko: "event:/sfx/relics/akabeko" },
    };
    render(
      <SoundFixture on map={map} emit={emit}>
        <Shop step={s} onChoose={() => {}} />
      </SoundFixture>,
    );
    fireEvent.click(screen.getByRole("button", { name: /Akabeko/ }));
    expect(emit).toHaveBeenCalledTimes(1);
    expect((emit.mock.calls[0][0] as { event: string }[])[0].event).toBe("event:/sfx/relics/akabeko");
  });

  it("falls back to the generic relic sound when the relic has none of its own", () => {
    const emit = vi.fn();
    const map: SfxMap = {
      version: "test", generic: { relic: "event:/sfx/ui/relic_activate_general" },
      cards: {}, powers: {}, monsters: {}, relics: {},
    };
    render(
      <SoundFixture on map={map} emit={emit}>
        <Shop step={s} onChoose={() => {}} />
      </SoundFixture>,
    );
    fireEvent.click(screen.getByRole("button", { name: /Anchor/ }));
    expect((emit.mock.calls[0][0] as { event: string }[])[0].event).toBe("event:/sfx/ui/relic_activate_general");
  });

  it("still calls onChoose when sound is off", () => {
    const onChoose = vi.fn();
    render(<Shop step={s} onChoose={onChoose} />);
    fireEvent.click(screen.getByRole("button", { name: /Akabeko/ }));
    expect(onChoose).toHaveBeenCalledWith("buy_relic", { relic_index: 0 });
  });
});
