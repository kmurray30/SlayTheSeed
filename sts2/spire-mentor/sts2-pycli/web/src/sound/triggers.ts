import type { CombatState } from "../api";
import type { EffectEvent } from "../effects/types";
import { resolveMonsterAttack, type SfxMap } from "./sfxMap";
import { powerValenceLookup, type PowerValence } from "./powerValence";
import { cueForUi } from "./runCues";
import type { CueClass, SoundCue } from "./types";

export interface CombatCueContext {
  map: SfxMap;
  /** Enemy indices that struck this transition (from `effects/enemyTurn.ts::attackingEnemies`). */
  attackers: number[];
  enemyName: (index: number) => string | undefined;
  enemyIntent: (index: number) => { type: string; damage?: number; hits?: number } | undefined;
  /** The run's character CLASS name (`state.player.name`), for the player's
   *  own death cry. Optional: a caller that doesn't know it gets silence
   *  there, exactly as before this cue existed. */
  character?: string;
  /** The two snapshots this transition spans, for the cues that are a
   *  COUNT change rather than an `EffectEvent` — turn banners, card deal,
   *  exhaust, orbs. `combatDiff` deliberately does not model these: they have
   *  no visual in Board.tsx, and adding target-less members to `EffectEvent`
   *  for sound alone would push sound-only concepts into the visual layer.
   *  Optional so an existing caller keeps working unchanged. */
  prev?: CombatState;
  next?: CombatState;
  /** Uniform [0,1), injected for the randomised families (see `cueForUi`). */
  rand?: () => number;
}

/**
 * The cues that come from a COUNT moving, not from an `EffectEvent`.
 *
 * All four are things the game voices through a banner or a per-card vfx that
 * the workbench renders differently or not at all, so there is nothing in
 * `combatDiff` to hang them on:
 *
 * - the two turn banners, once per round boundary (`NPlayerTurnBanner.cs:112`,
 *   `NEnemyTurnBanner.cs:84`). One workbench transition spans the end of your
 *   turn, the whole enemy turn and your next draw, so a round increment means
 *   BOTH banners fired, in that order.
 * - `card_deal` when the hand grew (`CardPileCmd.cs:875`, per card at 0.25
 *   volume — one cue here, since the player debounces per event anyway).
 * - `card_exhaust` when the exhaust pile grew (`NExhaustVfx.cs:102`).
 *
 * A missing `prev`/`next` yields nothing rather than guessing, and so does the
 * first snapshot of a combat (no `prev` to diff).
 */
function rhythmCues(ctx: CombatCueContext): SoundCue[] {
  const { prev, next, map } = ctx;
  if (!prev || !next) return [];
  // Direct `cueForUi(...)` calls, not a kinds array — see the same note in
  // `appSoundCues.arrivalCues`: the coverage sweep looks for the kind literal
  // at a call site.
  const cues: (SoundCue | null)[] = [];
  if (next.round > prev.round) {
    cues.push(cueForUi(map, "enemyTurn", ctx.rand), cueForUi(map, "playerTurn", ctx.rand));
  }
  if ((next.hand?.length ?? 0) > (prev.hand?.length ?? 0)) {
    cues.push(cueForUi(map, "cardDeal", ctx.rand));
  }
  if ((next.exhaust_pile_count ?? 0) > (prev.exhaust_pile_count ?? 0)) {
    cues.push(cueForUi(map, "cardExhaust", ctx.rand));
  }
  // Necrobinder's Osty arriving (`OstyCmd.cs:47`). `osty` stays on the wire
  // once he exists — with `alive: false` when slain — so this keys on the
  // field APPEARING, not on him being alive, and a resurrect after a death
  // therefore does not re-summon. That matches `OstyCmd`, which plays this
  // only on the initial summon.
  if (!prev.osty && next.osty) cues.push(cueForUi(map, "summon", ctx.rand));
  return cues.filter((c) => c !== null);
}

/**
 * Channel / evoke / passive, from the orb queue changing.
 *
 * The wire sends the whole queue each snapshot, so a type present in `next`
 * beyond its `prev` count was CHANNELLED and one short of it was EVOKED —
 * matching by type rather than by list position, since evoking the front orb
 * shifts every index behind it. Counting by type also gets a same-turn
 * channel-then-evoke of two different orbs right, which a length comparison
 * could not see at all.
 *
 * `passive` fires on the round boundary for every orb still in the queue —
 * `OrbModel.PlayPassiveSfx` is called from each orb's own turn trigger, and
 * only Frost and Glass ever call it (see `load_orb_sfx`).
 */
function orbCues(ctx: CombatCueContext): SoundCue[] {
  const { prev, next, map } = ctx;
  if (!prev || !next || !map.orbs) return [];
  const count = (orbs: CombatState["orbs"]) => {
    const by = new Map<string, number>();
    for (const o of orbs ?? []) by.set(o.type, (by.get(o.type) ?? 0) + 1);
    return by;
  };
  const before = count(prev.orbs);
  const after = count(next.orbs);
  const cues: SoundCue[] = [];
  const add = (event: string | undefined) => {
    if (event) cues.push({ event, cls: "informative" });
  };
  for (const type of new Set([...before.keys(), ...after.keys()])) {
    const delta = (after.get(type) ?? 0) - (before.get(type) ?? 0);
    if (delta > 0) add(map.orbs[type]?.channel);
    else if (delta < 0) add(map.orbs[type]?.evoke);
  }
  if (next.round > prev.round) {
    for (const type of after.keys()) add(map.orbs[type]?.passive);
  }
  return cues;
}

// One transition can carry six `hurt` events (an AoE) or two `blockBreak`s.
// Playing each would machine-gun the mix, so each generic kind fires at most
// once per transition — the player's debounce is a second line of defence,
// not the first.
//
// `hurt` is NOT in here. It used to map to `blockHit`, which was simply the
// wrong sound: `CreatureCmd.cs` plays `block_hit` only on its `WasFullyBlocked`
// arm, i.e. when block ate the entire hit. A hit that lands plays the target's
// own material impact (`SfxCmd.PlayDamage` -> `MonsterModel.TakeDamageSfx`),
// which is per-monster and so is resolved below rather than through this table.
//
// `fullyBlocked` is ALSO not in here (moved out below `cuesForCombat`'s
// attacker loop) — it needs to be checked ahead of the enemy-turn/no-log
// fallback heuristic that shares its `block_hit` key, not folded into a
// same-pass table lookup that can't see that ordering.
const GENERIC: Record<string, { key: string; cls: CueClass }> = {
  heal: { key: "heal", cls: "informative" },
  blockGain: { key: "blockGain", cls: "informative" },
  blockBreak: { key: "blockBreak", cls: "informative" },
};

/**
 * Which generic sound a power change should play, given the valence
 * `powerValence.ts` resolved for this power AT THIS AMOUNT — the power's own
 * `Type` from sfx_map.json's `powers` table, flipped where the game's
 * `GetTypeForAmount` flips it (a negative Strength is a debuff). The caller
 * passes `valenceOf(e.power, e.amount)`, and `Board.tsx`'s panel colour reads
 * the same function, so the chime and the colour cannot disagree.
 *
 * `powerAdd` plays the valence straight: gaining a Debuff (e.g. Vulnerable)
 * sounds bad, gaining a Buff (e.g. Strength) sounds good. `powerRemove`
 * INVERTS it — losing a Debuff is good news, losing a Buff is bad, so it
 * plays the opposite chime from an add of the same power.
 *
 * A power absent from the table (not yet classified for this game version,
 * or a future update added one before the map was regenerated) still MUST
 * make a sound rather than going silent — `valence` is `undefined` for it,
 * and this falls back to the same fixed direction the file used before
 * per-power valence existed: gain -> buff chime, loss -> debuff chime.
 */
function powerCueKey(
  kind: "powerAdd" | "powerRemove",
  valence: PowerValence | undefined,
): "buff" | "debuff" {
  if (valence === undefined) return kind === "powerAdd" ? "buff" : "debuff";
  const isDebuff = kind === "powerAdd" ? valence === "Debuff" : valence === "Buff";
  return isDebuff ? "debuff" : "buff";
}

/**
 * Derive the sounds one combat transition should make. Pure — no WebAudio,
 * no React. `ctx.attackers` membership (who counts as having struck this
 * transition) is decided upstream by `effects/enemyTurn.ts::attackingEnemies`;
 * this function does not re-filter by `enemyIntent(...).type`, so an enemy
 * the caller includes as an attacker still gets an attack cue even if its
 * current intent reads `DeathBlow` rather than `Attack` (e.g. an exploding
 * bomb) — `resolveMonsterAttack` already restricts hit-count matching to the
 * monster's `Attack`-tagged moves and falls back to `defaultAttack`/the
 * generic hit sound, so this never fabricates a move that isn't there.
 */
export function cuesForCombat(events: EffectEvent[], ctx: CombatCueContext): SoundCue[] {
  const cues: SoundCue[] = [];
  const seen = new Set<string>();
  // Hoisted out of the event loop below: the lookup builds a Set from the map's
  // `powersAllowNegative` list, and the map does not change within one call.
  const valenceOf = powerValenceLookup(ctx.map);

  // Banners announce what is about to happen, so they lead. `seen` is seeded
  // from them for the same reason it exists at all — one sound per transition.
  for (const cue of [...rhythmCues(ctx), ...orbCues(ctx)]) {
    if (seen.has(cue.event)) continue;
    seen.add(cue.event);
    cues.push(cue);
  }

  for (const index of ctx.attackers) {
    const monster = ctx.map.monsters[ctx.enemyName(index) ?? ""];
    const event = resolveMonsterAttack(
      monster,
      ctx.enemyIntent(index),
      ctx.map.generic.blockHit,
    );
    if (event && !seen.has(event)) {
      seen.add(event);
      cues.push({ event, cls: "informative" });
    }
  }

  // The game's `block_hit` (`CreatureCmd.cs:400-406`) is creature-agnostic —
  // it fires on `WasFullyBlocked` alone, with no `Side` check — and Task
  // 12/13 already treat a `fullyBlocked` target the same way on either side
  // (the "Blocked" text, the block-spark vfx). So a direct `fullyBlocked`
  // event, from `combatDiff` (gated `sameRound`, see combatDiff.ts) or a
  // per-hit log row, is the primary source here and wins regardless of what
  // the fallback below would have concluded.
  const blockedEvent = events.some((e) => e.kind === "fullyBlocked");
  if (blockedEvent) {
    const event = ctx.map.generic.blockHit;
    if (event && !seen.has(event)) {
      seen.add(event);
      cues.push({ event, cls: "informative" });
    }
  }

  // The one case the direct event above can't cover: the ENEMY TURN's own
  // fully-blocked hit on the player. `combatDiff` only emits `fullyBlocked`
  // when `sameRound` — block also zeroes at end of turn, so a snapshot pair
  // spanning the enemy turn can't tell "absorbed a hit" from "expired" — and
  // a recording with no combat log carries no per-hit `fullyBlocked` row at
  // all (Board.tsx synthesizes its attacks from the `attackingEnemies`
  // intent-guess instead). This is inherently PLAYER-only, not a legacy
  // leftover to generalise: `ctx.attackers` only ever names enemies striking
  // the PLAYER, so there is no symmetric "who did the player attack" input to
  // widen it with. Skipped whenever the direct check above already fired —
  // it would only re-derive the same `block_hit` key.
  if (
    !blockedEvent &&
    ctx.attackers.length &&
    !events.some((e) => e.kind === "hurt" && e.target.side === "player")
  ) {
    const event = ctx.map.generic.blockHit;
    if (event && !seen.has(event)) {
      seen.add(event);
      cues.push({ event, cls: "informative" });
    }
  }

  for (const e of events) {
    if (e.kind === "hurt") {
      // The sound of an attack LANDING, and it belongs to the body it lands on:
      // armor, fur, slime, stone… A monster with no entry of its own falls back
      // to `generic.impact`, which is MonsterModel's own `Armor` default.
      //
      // Enemy-only, exactly as in the decomp: `CreatureCmd.cs` guards the
      // `PlayDamage` call with `Side == CombatSide.Enemy`. A player taking a hit
      // is voiced by the attacker's own attack cue, played in the loop above —
      // giving the player an impact too would double every incoming hit.
      if (e.target.side !== "enemy") continue;
      const monster = ctx.map.monsters[ctx.enemyName(e.target.index) ?? ""];
      // The creature's VOICE, before its body's impact and in ADDITION to it —
      // `CreatureCmd.cs:326-328` plays `Monster.HurtSfx` alongside the "Hit"
      // animation trigger, then `:352` plays the material impact. Only 14 of
      // the 97 monsters declare one (`MonsterModel.HurtSfx` is virtual `null`),
      // and this table sat in the map unread until now — the same shape of gap
      // as the card hit sounds: mapped, shipped, and reachable by nothing.
      const voice = monster?.hurt;
      if (voice && !seen.has(voice)) {
        seen.add(voice);
        cues.push({ event: voice, cls: "informative" });
      }
      const event = monster?.impact ?? ctx.map.generic.impact;
      if (!event || seen.has(event)) continue;
      seen.add(event);
      cues.push({ event, cls: "informative" });
      continue;
    }
    if (e.kind === "death") {
      // The player dies to their own character's cry (`SfxCmd.PlayDeath(player)`
      // -> `player.Character.DeathSfx`, SfxCmd.cs:98). Note the game ALSO
      // starts `event:/temp/sfx/game_over` here, but as MUSIC on a different
      // bus (`CreatureCmd.cs:460`) — that one is emitted separately by
      // `runEndCue`, so the two do not duplicate.
      //
      // A monster whose class declares no `DeathSfx` stays silent rather than
      // borrowing another monster's.
      // Every monster fades out with the same sound on top of whatever its own
      // class names — `NMonsterDeathVfx.cs:227` plays `enemy_fade` for all of
      // them, and only 91 of the 97 declare a `DeathSfx` at all, so without
      // this the majority of kills were silent.
      if (e.target.side === "enemy") {
        const fade = ctx.map.generic.enemyFade;
        if (fade && !seen.has(fade)) {
          seen.add(fade);
          cues.push({ event: fade, cls: "informative" });
        }
      }
      const event =
        e.target.side === "enemy"
          ? ctx.map.monsters[ctx.enemyName(e.target.index) ?? ""]?.death
          : ctx.map.characters?.[ctx.character ?? ""]?.die;
      if (!event || seen.has(event)) continue;
      seen.add(event);
      cues.push({ event, cls: "informative" });
      continue;
    }
    if (e.kind === "powerAdd" || e.kind === "powerRemove") {
      const key = powerCueKey(e.kind, valenceOf(e.power, e.amount));
      const event = ctx.map.generic[key];
      if (!event || seen.has(event)) continue;
      seen.add(event);
      // Ruling 47 (final review finding 8, 2026-08-23) made `powerAdd`
      // informative and left `powerRemove` as `chatter`: Board.tsx:257
      // rendered the buff/debuff pop for `powerAdd` with no mode gate at
      // all, so `informative` (kept in `reduced`) mirrored it; but
      // Board.tsx:258 was `else if (e.kind === "powerRemove") { /* no pop on
      // remove for v1 */ }` -- there was NO visual for a removal, in ANY
      // mode, so `chatter` (dropped in `reduced`) was the classification
      // that actually matched. Task 12 gave `powerRemove` its own visual
      // (icon + name + "Wears Off" panel, `power_removed_vfx`), which
      // falsifies that premise: `chatter` would now make sound quieter than
      // what's on screen. Both kinds are `informative` as of this fix --
      // they are symmetric on the visual side again.
      cues.push({ event, cls: "informative" });
      continue;
    }
    const spec = GENERIC[e.kind];
    if (!spec) continue;
    const event = ctx.map.generic[spec.key];
    if (!event || seen.has(event)) continue;
    seen.add(event);
    cues.push({ event, cls: spec.cls });
  }
  return cues;
}

/** Apply the visual fx mode to sound, so the two never disagree. */
export function filterForMode(cues: SoundCue[], mode: "full" | "reduced" | "off"): SoundCue[] {
  if (mode === "off") return [];
  if (mode === "reduced") return cues.filter((c) => c.cls === "informative");
  return cues;
}
