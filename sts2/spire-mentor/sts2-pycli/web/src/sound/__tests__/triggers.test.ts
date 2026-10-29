import { describe, it, expect } from "vitest";
import { cuesForCombat, filterForMode } from "../triggers";
import { combatDiff } from "../../effects/combatDiff";
import type { CombatState } from "../../api";
import type { SfxMap } from "../sfxMap";
import type { EffectEvent, TargetRef } from "../../effects/types";

const PLAYER: TargetRef = { side: "player" };

const MAP: SfxMap = {
  version: "test",
  generic: {
    blockGain: "event:/sfx/block_gain",
    blockBreak: "event:/sfx/block_break",
    blockHit: "event:/sfx/block_hit",
    buff: "event:/sfx/buff",
    debuff: "event:/sfx/debuff",
    heal: "event:/sfx/heal",
    impact: "event:/sfx/enemy/enemy_impact_armor",
  },
  powers: { Vulnerable: "Debuff", Strength: "Buff" },
  powersAllowNegative: ["Strength"],
  cards: {},
  relics: {},
  // "Owl Magistrate" (a multi-word DISPLAY TITLE, not the class name
  // "OwlMagistrate") deliberately, so these tests exercise the same
  // namespace `Board.tsx`'s `enemyName` actually resolves from the wire
  // (`Enemy.name`) — see the 2026-08-23 final review, finding 1: every
  // fixture here used to key by class name, which let the real keying bug
  // (sts2-wiki/scripts/build_sfx_map.py keying `monsters` by `cs.stem`) ship
  // silently, since both sides of the test agreed on the wrong namespace.
  monsters: {
    "Owl Magistrate": {
      moves: [{ name: "PECK", intent: "Attack", hits: 6, event: "event:/sfx/e/peck" }],
      defaultAttack: "event:/sfx/e/peck",
      impact: "event:/sfx/enemy/enemy_impact_fur",
    },
  },
};

const ctx = {
  map: MAP,
  attackers: [] as number[],
  enemyName: () => undefined,
  enemyIntent: () => undefined,
};

describe("cuesForCombat", () => {
  it("plays the block-gain sound when block goes up", () => {
    const events: EffectEvent[] = [{ kind: "blockGain", target: { side: "player" }, amount: 5 }];
    const cues = cuesForCombat(events, ctx);
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/block_gain"]);
    // A block-gain cue carries useful information (not texture), so `reduced`
    // mode must not drop it.
    expect(cues[0].cls).toBe("informative");
  });

  it("plays block-break exactly once no matter how many creatures shatter", () => {
    const events: EffectEvent[] = [
      { kind: "blockBreak", target: { side: "player" } },
      { kind: "blockBreak", target: { side: "enemy", index: 0 } },
    ];
    expect(cuesForCombat(events, ctx)).toHaveLength(1);
  });

  it("collapses a six-target AoE into one hit cue", () => {
    const events: EffectEvent[] = [0, 1, 2, 3, 4, 5].map((index) => ({
      kind: "hurt" as const, target: { side: "enemy" as const, index }, amount: 4,
    }));
    expect(cuesForCombat(events, ctx)).toHaveLength(1);
  });

  it("plays the struck monster's material impact, not the block sound", () => {
    // `block_hit` is what CreatureCmd.cs plays when block ate the WHOLE hit.
    // A hit that lands plays the body it landed on — this used to be wrong for
    // every single landed hit in the game.
    const cues = cuesForCombat(
      [{ kind: "hurt", target: { side: "enemy", index: 0 }, amount: 9 }],
      { ...ctx, enemyName: () => "Owl Magistrate" },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/enemy/enemy_impact_fur"]);
  });

  it("falls back to the default impact for a monster with no entry", () => {
    const cues = cuesForCombat(
      [{ kind: "hurt", target: { side: "enemy", index: 0 }, amount: 9 }],
      { ...ctx, enemyName: () => "Someone New" },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/enemy/enemy_impact_armor"]);
  });

  // Mutation caught: dropping the voice layer. `MonsterSfx.hurt` sat in the
  // shipped map, resolved and verified, with NOTHING reading it — the same
  // shape of gap as the card hit sounds.
  it("plays the creature's own hurt voice BEFORE its body's impact", () => {
    const cues = cuesForCombat(
      [{ kind: "hurt", target: { side: "enemy", index: 0 }, amount: 9 }],
      {
        ...ctx,
        enemyName: () => "Mecha Knight",
        map: {
          ...ctx.map,
          monsters: {
            ...ctx.map.monsters,
            "Mecha Knight": { moves: [], hurt: "event:/sfx/e/mecha_hurt", impact: "event:/sfx/e/mecha_impact" },
          },
        },
      },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/e/mecha_hurt", "event:/sfx/e/mecha_impact"]);
  });

  it("plays only the impact for the 83 monsters that declare no hurt voice", () => {
    // `MonsterModel.HurtSfx` is virtual `null`; absent beats borrowed.
    const cues = cuesForCombat(
      [{ kind: "hurt", target: { side: "enemy", index: 0 }, amount: 9 }],
      { ...ctx, enemyName: () => "Owl Magistrate" },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/enemy/enemy_impact_fur"]);
  });

  it("gives the player no impact of their own when hurt", () => {
    // CreatureCmd.cs guards PlayDamage with `Side == CombatSide.Enemy`; the
    // attacker's own attack cue is what voices an incoming hit.
    expect(cuesForCombat([{ kind: "hurt", target: { side: "player" }, amount: 9 }], ctx)).toEqual([]);
  });

  it("plays block-hit from a direct fullyBlocked event, either side (CreatureCmd.cs is creature-agnostic)", () => {
    const cues = cuesForCombat([{ kind: "fullyBlocked", target: { side: "enemy", index: 0 } }], ctx);
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/block_hit"]);
  });

  it("plays block-hit when an enemy struck and the player lost no HP (the enemy-turn/no-log fallback)", () => {
    // combatDiff gates its own `fullyBlocked` on `sameRound` (block also
    // zeroes at end of turn, so a snapshot pair spanning the enemy turn can't
    // tell "absorbed a hit" from "expired") and a no-log recording never
    // produces a per-hit `fullyBlocked` row at all — this is the fallback
    // that covers both, off `ctx.attackers` instead of an event.
    const cues = cuesForCombat([], {
      ...ctx, attackers: [0], enemyName: () => "Owl Magistrate",
      enemyIntent: () => ({ type: "Attack", hits: 6 }),
    });
    expect(cues.map((c) => c.event)).toContain("event:/sfx/block_hit");
  });

  it("still plays block_hit when a hit is absorbed AND another lands in the same action", () => {
    // A hit fully absorbed while the player ALSO takes damage from another
    // hit in the same action: the "HP never moved" heuristic requires the
    // player's HP to be completely untouched, so on its own it would miss
    // this. The cue must not depend on the heuristic passing.
    //
    // What this test does NOT pin, despite the shape suggesting it: WHICH of
    // the two sources produced the cue. Pre-Task-15 code reaches the same
    // event by a different route (the `GENERIC` table lookup at the bottom of
    // the events loop), so this passes against that shape too — verified by
    // reverting `triggers.ts` and re-running. It pins the OUTCOME, which is
    // the part that must survive a future refactor; the routing is covered by
    // the two tests above, which pin each source in isolation. Named for what
    // it checks, because an earlier name claimed the discrimination it lacks.
    const cues = cuesForCombat(
      [{ kind: "fullyBlocked", target: PLAYER }, { kind: "hurt", target: PLAYER, amount: 3 }],
      {
        ...ctx, attackers: [0], enemyName: () => "Owl Magistrate",
        enemyIntent: () => ({ type: "Attack", hits: 6 }),
      },
    );
    expect(cues.some((c) => c.event === ctx.map.generic.blockHit)).toBe(true);
  });

  it("plays no block-hit when the enemy's attack got through", () => {
    const cues = cuesForCombat(
      [{ kind: "hurt", target: { side: "player" }, amount: 7 }],
      { ...ctx, attackers: [0], enemyName: () => "Owl Magistrate",
        enemyIntent: () => ({ type: "Attack", hits: 6 }) },
    );
    expect(cues.map((c) => c.event)).not.toContain("event:/sfx/block_hit");
    expect(cues.map((c) => c.event)).toContain("event:/sfx/e/peck");
  });

  it("plays a named enemy attack when that enemy struck", () => {
    const cues = cuesForCombat([], {
      ...ctx,
      attackers: [0],
      enemyName: () => "Owl Magistrate",
      enemyIntent: () => ({ type: "Attack", hits: 6 }),
    });
    expect(cues.map((c) => c.event)).toContain("event:/sfx/e/peck");
    // An enemy strike is informative, not chatter — `reduced` mode must keep it.
    expect(cues.find((c) => c.event === "event:/sfx/e/peck")?.cls).toBe("informative");
  });

  it("degrades an unknown monster to the generic hit sound", () => {
    const cues = cuesForCombat([], {
      ...ctx,
      attackers: [0],
      enemyName: () => "NotAMonster",
      enemyIntent: () => ({ type: "Attack", hits: 1 }),
    });
    expect(cues.map((c) => c.event)).toContain("event:/sfx/block_hit");
  });

  it("collapses two attackers that resolve to the same sound into one cue", () => {
    // Final review finding 10 (minor, 2026-08-23): the `!seen.has(event)`
    // dedup in the attacker loop (triggers.ts:75) had no test — removing it
    // left all 549 tests green, even though two same-kind attackers would
    // double-fire without it (the player's debounce masks it in practice).
    // Two DIFFERENT indices, both unknown monsters, both degrading to the
    // same generic blockHit event.
    const cues = cuesForCombat([], {
      ...ctx,
      attackers: [0, 1],
      enemyName: () => "NotAMonster",
      enemyIntent: () => ({ type: "Attack", hits: 1 }),
    });
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/block_hit"]);
  });

  it("plays the heal sound when hp rises", () => {
    // Mutation caught: removing the `heal` entry from triggers.ts's GENERIC
    // map — the cue list would come back empty instead of the heal event.
    const events: EffectEvent[] = [{ kind: "heal", target: { side: "player" }, amount: 6 }];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/heal"]);
  });

  it("marks a power GAIN as informative, not chatter (Ruling 47)", () => {
    // Board.tsx:257 renders the buff/debuff pop for a power ADD with no
    // mode gate at all, so it stays visible in `reduced` -- the matching
    // sound must not be tagged `chatter` (which `reduced` drops), or sound
    // would only approximate the visual layer instead of mirroring it.
    const events: EffectEvent[] = [{ kind: "powerAdd", target: { side: "player" }, power: "Weak" }];
    expect(cuesForCombat(events, ctx)[0].cls).toBe("informative");
  });

  it("classifies powerRemove as informative now that it has a visual (supersedes Ruling 47)", () => {
    // Ruling 47 (final review finding 8, 2026-08-23) made `powerAdd`
    // informative and left `powerRemove` as `chatter` (which `reduced` mode
    // drops), because Board.tsx rendered a buff/debuff pop for `powerAdd`
    // with no mode gate at all but had NO visual whatsoever for a removal --
    // `chatter` was the one class that actually matched "no visual ever" for
    // that kind. Task 12 gave `powerRemove` its own visual (icon + name +
    // "Wears Off" panel, `power_removed_vfx`), which falsifies that premise:
    // leaving it `chatter` would now make sound quieter than what's on
    // screen. Both kinds are `informative` as of this fix.
    const events: EffectEvent[] = [
      { kind: "powerRemove", target: { side: "player" }, power: "Weak" },
    ];
    expect(cuesForCombat(events, ctx)[0].cls).toBe("informative");
  });

  it("plays the debuff sound when gaining a debuff (e.g. Vulnerable)", () => {
    const events: EffectEvent[] = [
      { kind: "powerAdd", target: { side: "player" }, power: "Vulnerable" },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/debuff"]);
  });

  it("plays the buff sound when gaining a buff (e.g. Strength)", () => {
    const events: EffectEvent[] = [
      { kind: "powerAdd", target: { side: "player" }, power: "Strength" },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/buff"]);
  });

  it("inverts on removal: losing a debuff plays the buff (good-news) sound", () => {
    const events: EffectEvent[] = [
      { kind: "powerRemove", target: { side: "player" }, power: "Vulnerable" },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/buff"]);
  });

  it("inverts on removal: losing a buff plays the debuff (bad-news) sound", () => {
    const events: EffectEvent[] = [
      { kind: "powerRemove", target: { side: "player" }, power: "Strength" },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/debuff"]);
  });

  it("plays the DEBUFF sound for a negative Strength, matching its panel colour", () => {
    // `GetTypeForAmount` (`PowerModel.cs:462-472`) — Strength is typed Buff,
    // but at a negative total the game renders and sounds it as a debuff. The
    // colour reads the same `powerValence.ts` rule, so the two cannot disagree.
    const events: EffectEvent[] = [
      { kind: "powerAdd", target: { side: "player" }, power: "Strength", amount: -3 },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/debuff"]);
  });

  it("inverts the negative case on removal too: losing negative Strength is good news", () => {
    const events: EffectEvent[] = [
      { kind: "powerRemove", target: { side: "player" }, power: "Strength", amount: -3 },
    ];
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/buff"]);
  });

  it("degrades a power absent from the table to a sound, not silence (add)", () => {
    const events: EffectEvent[] = [
      { kind: "powerAdd", target: { side: "player" }, power: "NotInTheMap" },
    ];
    // Fixed fallback direction: gain -> buff chime.
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/buff"]);
  });

  it("degrades a power absent from the table to a sound, not silence (remove)", () => {
    const events: EffectEvent[] = [
      { kind: "powerRemove", target: { side: "player" }, power: "NotInTheMap" },
    ];
    // Fixed fallback direction: loss -> debuff chime.
    expect(cuesForCombat(events, ctx).map((c) => c.event)).toEqual(["event:/sfx/debuff"]);
  });

  it("plays the dying enemy's own death sound", () => {
    const map: SfxMap = {
      ...MAP,
      // "The Obscura" (display title; class name "TheObscura") deliberately
      // multi-word — see the note on the MAP fixture's "Owl Magistrate".
      monsters: { "The Obscura": { moves: [], death: "event:/sfx/e/obscura_die" } },
    };
    const cues = cuesForCombat(
      [{ kind: "death", target: { side: "enemy", index: 0 } }],
      { map, attackers: [], enemyName: () => "The Obscura", enemyIntent: () => undefined },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/e/obscura_die"]);
  });

  it("stays silent for an enemy whose death sound the decomp does not name", () => {
    // `defaultAttack` is set (but no `death`) so this also pins that death
    // must NEVER borrow the attack sound as a fallback — only an absent
    // `death` key means silence.
    const map: SfxMap = {
      ...MAP,
      monsters: {
        Quiet: { moves: [], defaultAttack: "event:/sfx/e/quiet_attack" },
      },
    };
    const cues = cuesForCombat(
      [{ kind: "death", target: { side: "enemy", index: 0 } }],
      { map, attackers: [], enemyName: () => "Quiet", enemyIntent: () => undefined },
    );
    expect(cues).toEqual([]);
  });

  it("plays the player's own character death cry, never an enemy's", () => {
    // `SfxCmd.PlayDeath(player)` -> `player.Character.DeathSfx` (SfxCmd.cs:98).
    // The enemy entry is present so a mutation that reaches for the monster
    // table on the player branch produces a WRONG sound rather than none —
    // an assertion that could not tell those apart if the table were empty.
    const map: SfxMap = {
      ...MAP,
      monsters: { "The Obscura": { moves: [], death: "event:/sfx/e/obscura_die" } },
      characters: { Ironclad: { die: "event:/sfx/characters/ironclad/ironclad_die" } },
    };
    const cues = cuesForCombat(
      [{ kind: "death", target: { side: "player" } }],
      {
        map, attackers: [], enemyName: () => "The Obscura", enemyIntent: () => undefined,
        character: "Ironclad",
      },
    );
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/characters/ironclad/ironclad_die"]);
  });

  it("stays silent when the player dies and the map names no character death sound", () => {
    // A map from before the `characters` table existed (a stale deploy).
    // Borrowing the enemy's cry would be wrong; silence is the old behaviour.
    const map: SfxMap = {
      ...MAP,
      monsters: { "The Obscura": { moves: [], death: "event:/sfx/e/obscura_die" } },
    };
    const cues = cuesForCombat(
      [{ kind: "death", target: { side: "player" } }],
      {
        map, attackers: [], enemyName: () => "The Obscura", enemyIntent: () => undefined,
        character: "Ironclad",
      },
    );
    expect(cues).toEqual([]);
  });

  it("degrades a death-only monster's empty moves list to the generic hit sound when it attacks", () => {
    // Fix round 1 (task-8b): a monster kept in the map ONLY for its
    // death/hurt sound (e.g. the real FakeMerchantMonster, display title
    // "The Merchant???") has `moves: []` and no `defaultAttack`. If it's
    // ever listed as an attacker, resolution must degrade to the generic
    // fallback rather than throw on an empty array. Keyed by display title,
    // not the class name — see the note on the MAP fixture above.
    const map: SfxMap = {
      ...MAP,
      monsters: { "The Merchant???": { moves: [], death: "event:/sfx/e/merchant_die" } },
    };
    const cues = cuesForCombat([], {
      map,
      attackers: [0],
      enemyName: () => "The Merchant???",
      enemyIntent: () => ({ type: "Attack", hits: 1 }),
    });
    expect(cues.map((c) => c.event)).toEqual(["event:/sfx/block_hit"]);
  });
});

function state(over: Partial<CombatState> = {}): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
    player_powers: [], enemies: [], hand: [], ...over,
  } as CombatState;
}

describe("combatDiff -> cuesForCombat: heal wiring", () => {
  it("a snapshot pair whose hp rises produces a cue whose event is the heal entry", () => {
    // Runs combatDiff for real (not a hand-built EffectEvent), so this catches
    // deleting the `if (next.hp > prev.hp) out.push({ kind: "heal", ... })`
    // line from combatDiff.ts, which the GENERIC-table unit test above cannot.
    const prev = state({ player: { name: "Ironclad", hp: 40, max_hp: 80, block: 0 } });
    const next = state({ player: { name: "Ironclad", hp: 55, max_hp: 80, block: 0 } });
    const cues = cuesForCombat(combatDiff(prev, next), ctx);
    expect(cues.map((c) => c.event)).toContain("event:/sfx/heal");
  });
});

describe("filterForMode", () => {
  const cues = [
    { event: "a", cls: "informative" as const },
    { event: "b", cls: "chatter" as const },
  ];
  it("keeps everything on full", () => expect(filterForMode(cues, "full")).toHaveLength(2));
  it("drops chatter on reduced", () => {
    expect(filterForMode(cues, "reduced").map((c) => c.event)).toEqual(["a"]);
  });
  it("plays nothing on off", () => expect(filterForMode(cues, "off")).toHaveLength(0));
});

/** A minimal combat snapshot; only the fields the rhythm/orb cues read matter. */
function snap(over: Partial<CombatState> = {}): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Defect", hp: 70, max_hp: 70, block: 0 },
    player_powers: [], enemies: [], hand: [], ...over,
  } as CombatState;
}

const RHYTHM: SfxMap = {
  ...MAP,
  generic: {
    ...MAP.generic,
    playerTurn: "res://debug_audio/player_turn.mp3",
    enemyTurn: "res://debug_audio/enemy_turn.mp3",
    cardDeal: "res://debug_audio/card_deal.mp3",
    cardExhaust: "res://debug_audio/card_exhaust.mp3",
    summon: "event:/sfx/characters/necrobinder/necrobinder_summon",
    enemyFade: "event:/sfx/enemy/enemy_fade",
  },
  orbs: {
    Frost: {
      channel: "event:/sfx/characters/defect/defect_frost_channel",
      evoke: "res://debug_audio/frost_orb_evoke.mp3",
      passive: "res://debug_audio/frost_orb_passive.mp3",
    },
    // Glass deliberately has NO evoke: `GlassOrb` never calls
    // `PlayEvokeSfx()`, even though `glass_orb_evoke.mp3` ships.
    Glass: { channel: "event:/sfx/characters/defect/defect_glass_channel" },
  },
};

const orb = (index: number, type: string) => ({
  index, name: `${type} Orb`, type, passive: 0, evoke: 0,
});

function rhythm(prev: Partial<CombatState>, next: Partial<CombatState>): string[] {
  return cuesForCombat([], { ...ctx, map: RHYTHM, prev: snap(prev), next: snap(next) })
    .map((c) => c.event);
}

describe("cuesForCombat — turn rhythm", () => {
  // One workbench transition spans the end of your turn, the whole enemy turn
  // and your next draw, so a round increment means BOTH banners fired.
  it("plays both turn banners on a round boundary, enemy first", () => {
    expect(rhythm({ round: 1 }, { round: 2 })).toEqual([
      "res://debug_audio/enemy_turn.mp3",
      "res://debug_audio/player_turn.mp3",
    ]);
  });

  it("plays no banner within a round", () => {
    expect(rhythm({ round: 2 }, { round: 2 })).toEqual([]);
  });

  it("plays the deal sound when the hand grew", () => {
    expect(rhythm({ hand: [] }, { hand: [{ index: 0 }] } as Partial<CombatState>))
      .toEqual(["res://debug_audio/card_deal.mp3"]);
  });

  // Mutation caught: `!==` instead of `>`. Playing a card SHRINKS the hand and
  // must not sound like drawing one.
  it("plays no deal sound when the hand shrank", () => {
    expect(rhythm({ hand: [{ index: 0 }] } as Partial<CombatState>, { hand: [] })).toEqual([]);
  });

  it("plays the exhaust sound when the exhaust pile grew", () => {
    expect(rhythm({ exhaust_pile_count: 0 }, { exhaust_pile_count: 1 }))
      .toEqual(["res://debug_audio/card_exhaust.mp3"]);
  });

  it("emits nothing at all without both snapshots", () => {
    expect(cuesForCombat([], { ...ctx, map: RHYTHM })).toEqual([]);
  });

  // `osty` stays on the wire once he exists (with `alive:false` when slain),
  // so this keys on the field appearing, not on him being alive.
  it("plays the summon when Osty first appears", () => {
    expect(rhythm({}, { osty: { name: "Osty", hp: 10, max_hp: 10, alive: true } } as Partial<CombatState>))
      .toEqual(["event:/sfx/characters/necrobinder/necrobinder_summon"]);
  });
});

describe("cuesForCombat — orbs", () => {
  it("channels when an orb joins the queue", () => {
    expect(rhythm({ orbs: [] }, { orbs: [orb(0, "Frost")] } as Partial<CombatState>))
      .toEqual(["event:/sfx/characters/defect/defect_frost_channel"]);
  });

  it("evokes when an orb leaves it", () => {
    expect(rhythm({ orbs: [orb(0, "Frost")] } as Partial<CombatState>, { orbs: [] }))
      .toEqual(["res://debug_audio/frost_orb_evoke.mp3"]);
  });

  // Mutation caught: comparing list LENGTH instead of counting per type. A
  // same-transition channel of one type and evoke of another nets to zero.
  it("hears both a channel and an evoke of different types in one transition", () => {
    expect(rhythm(
      { orbs: [orb(0, "Frost")] } as Partial<CombatState>,
      { orbs: [orb(0, "Glass")] } as Partial<CombatState>,
    )).toEqual([
      // Types are visited prev-first, so the orb that left is heard before
      // the one that arrived — the order they happened in.
      "res://debug_audio/frost_orb_evoke.mp3",
      "event:/sfx/characters/defect/defect_glass_channel",
    ]);
  });

  it("stays silent for an orb slot the game never plays", () => {
    // Glass has a channel but no evoke — the gap is `GlassOrb`'s, not the map's.
    expect(rhythm({ orbs: [orb(0, "Glass")] } as Partial<CombatState>, { orbs: [] })).toEqual([]);
  });

  it("plays the passive of every orb still held, on the round boundary", () => {
    expect(rhythm(
      { round: 1, orbs: [orb(0, "Frost"), orb(1, "Glass")] } as Partial<CombatState>,
      { round: 2, orbs: [orb(0, "Frost"), orb(1, "Glass")] } as Partial<CombatState>,
    )).toEqual([
      "res://debug_audio/enemy_turn.mp3",
      "res://debug_audio/player_turn.mp3",
      "res://debug_audio/frost_orb_passive.mp3",
    ]);
  });
});

describe("cuesForCombat — monster death fade", () => {
  // Only 91 of the 97 monsters declare a `DeathSfx`; `NMonsterDeathVfx.cs:227`
  // plays the fade for ALL of them, so without it most kills were silent.
  it("fades every monster, whether or not its class names a death sound", () => {
    const died: EffectEvent[] = [{ kind: "death", target: { side: "enemy", index: 0 } }];
    expect(cuesForCombat(died, { ...ctx, map: RHYTHM, enemyName: () => "Nobody" }).map((c) => c.event))
      .toEqual(["event:/sfx/enemy/enemy_fade"]);
  });

  it("does not fade the PLAYER dying", () => {
    const died: EffectEvent[] = [{ kind: "death", target: { side: "player" } }];
    expect(cuesForCombat(died, { ...ctx, map: RHYTHM }).map((c) => c.event)).toEqual([]);
  });
});
