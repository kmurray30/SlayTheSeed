import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Combatant from "../Combatant";

/**
 * Task 12: the real game's three DOM VFX panels — blocked text
 * (`vfx_blocked_text.tscn`), power applied (`power_applied_vfx.tscn`) and
 * power removed (`power_removed_vfx.tscn`) — replacing the invented
 * `.gs-buffpop` chip (`▲ Strength`) that showed neither the power's own icon
 * nor its real name.
 *
 * Deviates from the task-12 brief per the controller addendum (read after the
 * brief; it wins on conflicts):
 *  - no `amount` assertion / field. `NPowerAppliedVfx.cs` stores `_amount`
 *    (line 137) and never reads it again — its Label is `_power.Title`, the
 *    NAME. `NPowerRemovedVfx.Create(PowerModel power)` takes no amount at
 *    all. Shipping a count here would repeat the exact invented-chip mistake
 *    this task deletes.
 *  - the power's NAME is asserted instead of a count.
 */
const props = { c: { name: "Cultist", hp: 40, max_hp: 40, block: 0 }, isEnemy: true };

describe("creature event VFX — the real power/blocked panels", () => {
  it("shows the power's own icon and name, not an invented chip", () => {
    render(<Combatant {...props} effects={{ nonce: 1, powerPop: {
      power: "Vulnerable", icon: "/img/vulnerable.png", kind: "debuff", mode: "applied",
    } }} />);
    expect(screen.getByRole("img", { name: /vulnerable/i })).toHaveAttribute("src", "/img/vulnerable.png");
    expect(screen.getByText("Vulnerable")).toBeInTheDocument();
    // The old `▲ Strength` chip is gone, not merely restyled.
    expect(screen.queryByText(/^[▲▽]/)).toBeNull();
  });

  it('renders "Blocked" for a fully absorbed hit, falling back when no manifest is supplied', () => {
    // No `locStrings` prop — the manifest hasn't loaded (or this caller,
    // like a preview harness, never fetches one). `vfx_blocked_text.tscn`'s
    // Label reads exactly "Blocked" either way — the scene's OWN authored
    // text happens to already match the loc row it is overwritten from at
    // runtime (`vfx/BLOCKED`), unlike `power_removed_vfx.tscn` below — but
    // this is the documented FALLBACK literal, not the manifest string.
    render(<Combatant {...props} effects={{ nonce: 1, blocked: true }} />);
    expect(screen.getByText("Blocked")).toBeInTheDocument();
  });

  it("prefers the manifest's own loc string over the fallback literal", () => {
    // A value that couldn't be mistaken for the fallback: pins that
    // `locStrings.blocked` — not the "Blocked" literal beside it — is what
    // actually renders when the manifest has loaded (fix round 1: the
    // literal was previously the ONLY source, with a comment falsely
    // claiming otherwise).
    render(<Combatant {...props} effects={{ nonce: 1, blocked: true }}
      locStrings={{ blocked: "BLOCKED (from manifest)" }} />);
    expect(screen.getByText("BLOCKED (from manifest)")).toBeInTheDocument();
    expect(screen.queryByText("Blocked")).toBeNull();
  });

  it("distinguishes an applied power from a removed one, and shows no colour on removal", () => {
    const { rerender } = render(<Combatant {...props} effects={{ nonce: 1, powerPop: {
      power: "Weak", icon: null, mode: "removed",
    } }} />);
    const removed = screen.getByTestId("power-pop");
    expect(removed).toHaveAttribute("data-mode", "removed");
    // `power_removed_vfx.tscn`: HBox[ TextureRect, VBox[ PowerField, WearsOff ] ].
    // WearsOff's authored "Where's Off?" is overwritten at _Ready from loc key
    // `vfx/POWER_WEARS_OFF` = "Wears Off" — never the placeholder text.
    expect(screen.getByText("Wears Off")).toBeInTheDocument();
    expect(screen.getByText("Weak")).toBeInTheDocument();
    // No buff/debuff colour class on a removal — NPowerRemovedVfx has no
    // `_isBuff` field at all to drive one.
    expect(removed.className).not.toMatch(/gs-buff|gs-debuff/);

    rerender(<Combatant {...props} effects={{ nonce: 2, powerPop: {
      power: "Weak", icon: null, kind: "debuff", mode: "applied",
    } }} />);
    const applied = screen.getByTestId("power-pop");
    expect(applied).toHaveAttribute("data-mode", "applied");
    // No "Wears Off" on an applied panel.
    expect(screen.queryByText("Wears Off")).toBeNull();
  });

  it("colours an applied buff green-class and an applied debuff red-class", () => {
    const { rerender, container } = render(<Combatant {...props} effects={{ nonce: 1, powerPop: {
      power: "Strength", icon: null, kind: "buff", mode: "applied",
    } }} />);
    expect(container.querySelector(".gs-power-pop-label.gs-buff")).not.toBeNull();

    rerender(<Combatant {...props} effects={{ nonce: 2, powerPop: {
      power: "Vulnerable", icon: null, kind: "debuff", mode: "applied",
    } }} />);
    expect(container.querySelector(".gs-power-pop-label.gs-debuff")).not.toBeNull();
  });

  it("prefers the manifest's 'Wears Off' string over the fallback literal", () => {
    render(<Combatant {...props} effects={{ nonce: 1, powerPop: {
      power: "Weak", icon: null, mode: "removed",
    } }} locStrings={{ powerWearsOff: "WEARS OFF (from manifest)" }} />);
    expect(screen.getByText("WEARS OFF (from manifest)")).toBeInTheDocument();
    expect(screen.queryByText("Wears Off")).toBeNull();
  });

  it("renders no icon <img> when the power has no art (falls back like Powers.tsx's chip)", () => {
    render(<Combatant {...props} effects={{ nonce: 1, powerPop: {
      power: "Intangible", icon: null, kind: "buff", mode: "applied",
    } }} />);
    expect(screen.queryByRole("img", { name: /intangible/i })).toBeNull();
    expect(screen.getByText("Intangible")).toBeInTheDocument();
  });
});

describe("Task 13: the block badge is a stable VFX anchor across a block-break", () => {
  it("mounts [data-block-badge] even at block: 0, so the anchor survives the transition that empties it", () => {
    // The bug this pins: `Board.tsx`'s `blockBreak` handling runs in a
    // `useEffect` that fires AFTER `c.block` has already committed to 0 in
    // this same render. A badge gated on `c.block > 0` (or even a same-tick
    // `Fx` flag meant to bridge the gap) is ALREADY GONE from the DOM by the
    // time `VfxLayer`'s `badgeRect` goes looking for it — verified by
    // tracing React's render-then-effect ordering, not assumed. The fix is
    // this: the badge div itself never unmounts: only its number/opacity
    // toggle with `c.block`.
    const { container, rerender } = render(<Combatant {...props} c={{ ...props.c, block: 12 }} />);
    expect(container.querySelector("[data-block-badge]")).not.toBeNull();
    rerender(<Combatant {...props} c={{ ...props.c, block: 0 }} />);
    // This is the exact moment `VfxLayer.badgeRect` would be queried for a
    // `blockBreak` event fired off THIS transition — the anchor must still
    // resolve, not have vanished along with the number.
    expect(container.querySelector("[data-block-badge]")).not.toBeNull();
  });

  it("hides the badge's number/paint at block: 0 without unmounting the anchor point", () => {
    const { container } = render(<Combatant {...props} c={{ ...props.c, block: 0 }} />);
    const badge = container.querySelector("[data-block-badge]");
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toBe("");
    expect(badge).toHaveStyle({ opacity: "0" });
  });

  it("shows the count and full opacity once block is gained", () => {
    const { container } = render(<Combatant {...props} c={{ ...props.c, block: 5 }} />);
    const badge = container.querySelector("[data-block-badge]");
    expect(badge?.textContent).toBe("5");
    expect(badge).not.toHaveStyle({ opacity: "0" });
  });
});
