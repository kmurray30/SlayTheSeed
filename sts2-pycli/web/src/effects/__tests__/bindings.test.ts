import { describe, it, expect, vi } from "vitest";
import {
  loadBindings,
  effectsForEvent,
  ownerKeyCandidates,
  reportSubstitution,
  deathFadeFor,
  shouldFadeAfterDeath,
  type BindingAliases,
  type Bindings,
  type DeathFadeRow,
  type BindingEffectInfo,
  type BindingRow,
} from "../bindings";
import type { EffectEvent, EffectSource, TargetRef } from "../types";

const E: TargetRef = { side: "enemy", index: 0 };

/** Build a minimal `Bindings` from `{owner: {effect, tint?}}` rows (or a
 *  LIST of them, for the owners that carry more than one) plus an optional
 *  `effects` renderability map. `kind`/`shape` are filled with values no
 *  test here cares about. */
type Row = { effect: string; tint?: string };

function makeBindings(
  rows: Record<string, Row | Row[]>,
  effects: Record<string, BindingEffectInfo> = {},
  aliases?: BindingAliases,
): Bindings {
  const bindings: BindingRow[] = Object.entries(rows).flatMap(([owner, r]) =>
    (Array.isArray(r) ? r : [r]).map((one) => ({
      owner,
      kind: owner.includes(":") ? ("move" as const) : ("card" as const),
      effect: one.effect,
      tint: one.tint ?? null,
      shape: "hit_fx",
    })),
  );
  return { version: "test", effects, bindings, aliases };
}

/** The single pick for a source the table binds to exactly one effect —
 *  most rows. Asserts the one-ness rather than quietly taking `[0]`. */
function onlyPick(e: EffectEvent, b: Bindings | null) {
  const picks = effectsForEvent(e, b);
  expect(picks).toHaveLength(1);
  return picks[0]!;
}

function hurtFrom(source: EffectSource | undefined): EffectEvent {
  return { kind: "hurt", target: E, amount: 6, source };
}

function blockGain(): EffectEvent {
  return { kind: "blockGain", target: { side: "player" }, amount: 5 };
}

describe("loadBindings", () => {
  it("fetches the table with a cache-busting parameter", async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true, json: async () => makeBindings({}),
    } as Response);
    await loadBindings(fetchImpl as unknown as typeof fetch);
    const url = String(fetchImpl.mock.calls[0]![0]);
    expect(url).toContain("vfx/vfx_bindings.json");
    expect(url).toMatch(/[?&]v=/);
  });

  it("returns the parsed table", async () => {
    const table = makeBindings({ BASH: { effect: "vfx_attack_blunt" } });
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, json: async () => table } as Response);
    const b = await loadBindings(fetchImpl as unknown as typeof fetch);
    expect(b.bindings[0]!.owner).toBe("BASH");
  });

  it("rejects on a failed fetch rather than returning an empty table", async () => {
    // A 404 here must not masquerade as "not fetched yet" (the legitimate
    // `null` race `effectsForEvent` tolerates) — a caller that silently
    // swallowed this rejection would render vfx_attack_slash for every hit
    // in production while looking healthy.
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const fetchImpl = vi.fn().mockResolvedValue({ ok: false, status: 404 } as Response);
    await expect(loadBindings(fetchImpl as unknown as typeof fetch)).rejects.toThrow();
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });
});

describe("effectsForEvent", () => {
  it("plays the effect the table names", () => {
    const b = makeBindings({ BASH: { effect: "vfx_attack_blunt" } });
    expect(onlyPick(hurtFrom({ kind: "card", id: "BASH" }), b))
      .toEqual({ effect: "vfx_attack_blunt", anchor: "center", tint: null });
  });

  it("falls back to the table's declared fallback for a non-renderable effect", () => {
    const b = makeBindings(
      { "X:Y_MOVE": { effect: "vfx_heavy_blunt" } },
      { vfx_heavy_blunt: { renderable: false, fallback: "vfx_attack_blunt" } },
    );
    const r = onlyPick(hurtFrom({ kind: "move", monster: "X", id: "Y" }), b);
    expect(r).toMatchObject({ effect: "vfx_attack_blunt", fellBackFrom: "vfx_heavy_blunt" });
  });

  it("records the substitution rather than hiding it", () => {
    // A silent fallback is indistinguishable from a correct row, which is
    // how a bad table row would survive review.
    const b = makeBindings({}, {});
    const r = onlyPick(hurtFrom({ kind: "card", id: "UNKNOWN" }), b);
    expect(r.fellBackFrom).toBe("<unbound>");
  });

  it("keeps the slash default for an event with no source at all", () => {
    expect(onlyPick(hurtFrom(undefined), makeBindings({}))).toMatchObject({
      effect: "vfx_attack_slash",
    });
  });

  it("behaves exactly as before when the table has not loaded yet", () => {
    // The table is fetched; the first frames of a fight can race it.
    // Dropping effects for those frames would be a regression against
    // sub-project 1.
    expect(onlyPick(hurtFrom({ kind: "card", id: "BASH" }), null))
      .toMatchObject({ effect: "vfx_attack_slash" });
    expect(onlyPick(blockGain(), null)).toMatchObject({ effect: "vfx_block" });
  });

  it("carries the table's tint", () => {
    // Tints are `#`-prefixed as of task 6 — the real table has no bare-hex
    // rows.
    const b = makeBindings({ "F:SPORE_MOVE": { effect: "vfx_spore_impact", tint: "#8aad7d" } });
    expect(onlyPick(hurtFrom({ kind: "move", monster: "F", id: "SPORE" }), b).tint)
      .toBe("#8aad7d");
  });

  it("falls back to the bare id when the `_MOVE`-suffixed key has no row", () => {
    // Task 7's Rust gate measured 30 of 226 move rows (13%) with no `_MOVE`
    // suffix at all (e.g. AxeRubyRaider:SWING_1, FrogKnight:BEETLE_CHARGE,
    // OwlMagistrate:VERDICT) — the join must try both candidates, in that
    // order, or it silently misses this minority.
    const b = makeBindings({ "AxeRubyRaider:SWING_1": { effect: "vfx_attack_slash" } });
    const r = onlyPick(hurtFrom({ kind: "move", monster: "AxeRubyRaider", id: "SWING_1" }), b);
    expect(r).toMatchObject({ effect: "vfx_attack_slash" });
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("prefers the `_MOVE`-suffixed key over the bare id when both exist", () => {
    const b = makeBindings({
      "M:STATE_MOVE": { effect: "vfx_attack_slash" },
      "M:STATE": { effect: "vfx_attack_blunt" },
    });
    const r = onlyPick(hurtFrom({ kind: "move", monster: "M", id: "STATE" }), b);
    expect(r.effect).toBe("vfx_attack_slash");
  });

  it("is not table-driven for blockGain — the table has no rows for it", () => {
    const b = makeBindings({ DEFEND: { effect: "vfx_attack_slash" } });
    expect(onlyPick(blockGain(), b)).toEqual({ effect: "vfx_block", anchor: "center", tint: null });
  });

  it("plays the block spark on the CREATURE for a fully-blocked hit — fixed, not table-driven", () => {
    const b = makeBindings({});
    expect(onlyPick({ kind: "fullyBlocked", target: E }, b)).toEqual({
      effect: "vfx_block_spark_vfx",
      anchor: "center",
      tint: null,
    });
  });

  it("plays the block-break halves on the BADGE, not the creature, when block empties out", () => {
    const b = makeBindings({});
    expect(onlyPick({ kind: "blockBreak", target: E }, b)).toEqual({
      effect: "vfx_block_broken",
      anchor: "badge",
      tint: null,
    });
  });

  it("plays the power flash on the creature for a powerFlash event — fixed, not table-driven", () => {
    const b = makeBindings({});
    expect(onlyPick({ kind: "powerFlash", target: E, power: "Thorns" }, b)).toEqual({
      effect: "vfx_power_flash_vfx",
      anchor: "center",
      tint: null,
    });
  });

  it("plays the fixed block/power events even with no table fetched yet", () => {
    expect(onlyPick({ kind: "fullyBlocked", target: E }, null)).toMatchObject({ effect: "vfx_block_spark_vfx" });
    expect(onlyPick({ kind: "blockBreak", target: E }, null)).toMatchObject({ effect: "vfx_block_broken" });
    expect(onlyPick({ kind: "powerFlash", target: E, power: "Thorns" }, null)).toMatchObject({
      effect: "vfx_power_flash_vfx",
    });
  });

  it("plays EVERY row an owner carries, not the last one to win a map insert", () => {
    // 15 owners in the real table chain more than one spawn call in the same
    // C# builder (SOVEREIGN_BLADE chains three) and the game plays all of
    // them. Indexing them into a `new Map(...)` was last-wins and dropped 16
    // rows silently.
    const b = makeBindings({
      "Wriggler:NASTY_BITE_MOVE": [{ effect: "vfx_bite" }, { effect: "vfx_wormy_impact" }],
    });
    const picks = effectsForEvent(hurtFrom({ kind: "move", monster: "Wriggler", id: "NASTY_BITE" }), b);
    expect(picks.map((p) => p.effect)).toEqual(["vfx_bite", "vfx_wormy_impact"]);
  });

  it("keeps each row's own tint when an owner carries several", () => {
    const b = makeBindings({
      "MechaKnight:CHARGE_MOVE": [
        { effect: "vfx_spike_splash_vfx" },
        { effect: "vfx_big_slash_impact", tint: "#80dbff" },
      ],
    });
    const picks = effectsForEvent(hurtFrom({ kind: "move", monster: "MechaKnight", id: "CHARGE" }), b);
    expect(picks.map((p) => p.tint)).toEqual([null, "#80dbff"]);
  });

  it("resolves a monster whose moves live in another C# file", () => {
    // MysteriousKnight inherits FlailKnight's move machine wholesale, so the
    // table has no MysteriousKnight:* row and never will. Without the alias
    // this played the generic slash.
    const aliases: BindingAliases = {
      monsters: { MysteriousKnight: { monster: "FlailKnight", reason: "subclass, no override" } },
      states: {},
    };
    const b = makeBindings({ "FlailKnight:FLAIL_MOVE": { effect: "vfx_attack_blunt" } }, {}, aliases);
    const r = onlyPick(hurtFrom({ kind: "move", monster: "MysteriousKnight", id: "FLAIL" }), b);
    expect(r).toMatchObject({ effect: "vfx_attack_blunt" });
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("resolves an engine state id that is a rename, not a suffix difference", () => {
    const aliases: BindingAliases = {
      monsters: {},
      states: { "SpinyToad:LASH": { state: "TONGUE_LASH_MOVE", reason: "SpinyToad.cs:61" } },
    };
    const b = makeBindings({ "SpinyToad:TONGUE_LASH_MOVE": { effect: "vfx_attack_slash" } }, {}, aliases);
    const r = onlyPick(hurtFrom({ kind: "move", monster: "SpinyToad", id: "LASH" }), b);
    expect(r).toMatchObject({ effect: "vfx_attack_slash" });
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("consults a state alias LAST, so a real row always wins", () => {
    const aliases: BindingAliases = {
      monsters: {},
      states: { "M:STATE": { state: "OTHER_MOVE", reason: "test" } },
    };
    const b = makeBindings(
      { "M:STATE_MOVE": { effect: "vfx_attack_slash" }, "M:OTHER_MOVE": { effect: "vfx_attack_blunt" } },
      {},
      aliases,
    );
    expect(onlyPick(hurtFrom({ kind: "move", monster: "M", id: "STATE" }), b).effect)
      .toBe("vfx_attack_slash");
  });

  it("builds the same candidate keys the Rust coverage gate looks up", () => {
    const aliases: BindingAliases = {
      monsters: { DenseVegetationWriggler: { monster: "Wriggler", reason: "slot variant" } },
      states: { "Wriggler:NASTY_BITE": { state: "NASTY_BITE_MOVE_X", reason: "test" } },
    };
    expect(ownerKeyCandidates({ kind: "card", id: "BASH" }, aliases)).toEqual(["BASH"]);
    expect(
      ownerKeyCandidates({ kind: "move", monster: "DenseVegetationWriggler", id: "NASTY_BITE" }, aliases),
    ).toEqual([
      "Wriggler:NASTY_BITE_MOVE",
      "Wriggler:NASTY_BITE",
      "Wriggler:NASTY_BITE_MOVE_X",
    ]);
  });

  it("has no overlay effect for kinds sub-project 1 does not cover", () => {
    const b = makeBindings({});
    expect(effectsForEvent({ kind: "heal", target: E, amount: 3 }, b)).toEqual([]);
    expect(effectsForEvent({ kind: "death", target: E }, b)).toEqual([]);
    expect(effectsForEvent({ kind: "powerAdd", target: E, power: "Weak" }, b)).toEqual([]);
  });
});

describe("reportSubstitution", () => {
  it("surfaces each distinct substitution exactly once", () => {
    // `fellBackFrom` had no consumer at all, so every fallback and every
    // unbound move substituted silently — the state the field exists to
    // prevent. Deduped so a fight replaying one unbound move logs one line.
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const source: EffectSource = { kind: "move", monster: "Zzz", id: "NEVER_SEEN" };
    const pick = { effect: "vfx_attack_slash", anchor: "center" as const, tint: null, fellBackFrom: "<unbound>" };
    reportSubstitution(source, pick);
    reportSubstitution(source, pick);
    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0]![0])).toContain("Zzz:NEVER_SEEN");
    warn.mockRestore();
  });

  it("says nothing for a pick that played exactly what its row named", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    reportSubstitution({ kind: "card", id: "BASH" }, {
      effect: "vfx_attack_blunt", anchor: "center", tint: null,
    });
    expect(warn).not.toHaveBeenCalled();
    warn.mockRestore();
  });
});


describe("deathFadeFor / shouldFadeAfterDeath", () => {
  /** A table carrying only what these tests read. `deathFade` is keyed by C#
   *  file stem; the wire sends `MonsterKind`, and the two agree except for the
   *  engine-only variants `aliases.monsters` reconciles. */
  function withDeathFade(
    deathFade: Record<string, DeathFadeRow>,
    aliases?: BindingAliases,
  ): Bindings {
    return { version: "test", effects: {}, bindings: [], deathFade, aliases };
  }

  it("reads a monster the table has a row for", () => {
    const b = withDeathFade({ Fabricator: { fade: "never" } });
    expect(deathFadeFor("Fabricator", b)).toEqual({ fade: "never" });
    expect(shouldFadeAfterDeath("Fabricator", b)).toBe(false);
  });

  it("gives an absent monster MonsterModel's defaults", () => {
    // `MonsterModel.cs:311` is `=> true`, and the table publishes only the
    // exceptions — so absent MUST read as "fades", or gating on this would
    // silently stop every ordinary monster dissolving.
    const b = withDeathFade({ Fabricator: { fade: "never" } });
    expect(deathFadeFor("Cultist", b)).toEqual({});
    expect(shouldFadeAfterDeath("Cultist", b)).toBe(true);
  });

  it("resolves an engine-only kind through the published monster alias", () => {
    // Three `DecimillipedeSegment*` engine kinds, one C# class. Without the
    // alias hop, all three miss the row and dissolve — which is precisely the
    // bug class `aliases.monsters` was added for on the move-lookup side.
    const b = withDeathFade(
      { DecimillipedeSegment: { fade: "never" } },
      {
        monsters: {
          DecimillipedeSegmentFront: { monster: "DecimillipedeSegment", reason: "test" },
        },
        states: {},
      },
    );
    expect(shouldFadeAfterDeath("DecimillipedeSegmentFront", b)).toBe(false);
    // ...and a kind with no alias and no row is still the default.
    expect(shouldFadeAfterDeath("DecimillipedeSegmentBack", b)).toBe(true);
  });

  it("treats a conditional row as fading, and keeps its expression", () => {
    // `WaterfallGiant.cs:157` is `PressureBuildupIdx == 0`, which nothing here
    // can evaluate. Dissolving is what the board did before this gate existed,
    // so that is the direction to fail in — and the expression stays readable
    // for whoever implements it rather than being flattened into a bool.
    const b = withDeathFade({
      WaterfallGiant: { fade: "conditional", condition: "PressureBuildupIdx == 0" },
    });
    expect(shouldFadeAfterDeath("WaterfallGiant", b)).toBe(true);
    expect(deathFadeFor("WaterfallGiant", b).condition).toBe("PressureBuildupIdx == 0");
  });

  it("degrades to the defaults with no table, no kind, or no deathFade section", () => {
    // Three separate ways this can be unavailable — a fetch still in flight, a
    // producer that predates `kind` on the wire, and a fixture table. All three
    // must land on "dissolves", the pre-gate behaviour.
    expect(shouldFadeAfterDeath("Fabricator", null)).toBe(true);
    expect(shouldFadeAfterDeath(null, withDeathFade({ Fabricator: { fade: "never" } }))).toBe(true);
    expect(shouldFadeAfterDeath("Fabricator", { version: "t", effects: {}, bindings: [] })).toBe(true);
  });

  it("carries the death wait alongside the fade answer", () => {
    const b = withDeathFade({ Crusher: { fade: "never", deathAnimSeconds: 2.5 } });
    expect(deathFadeFor("Crusher", b).deathAnimSeconds).toBe(2.5);
  });
});
