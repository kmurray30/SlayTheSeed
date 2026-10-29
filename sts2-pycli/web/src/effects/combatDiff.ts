import type { CombatState, Power } from "../api";
import type { EffectEvent, TargetRef } from "./types";

interface CreatureSnap {
  target: TargetRef;
  hp: number;
  block: number;
  powers: Power[];
}

function diffCreature(
  prev: CreatureSnap, next: CreatureSnap, out: EffectEvent[], sameRound: boolean,
): void {
  const t = next.target;
  if (next.block > prev.block) out.push({ kind: "blockGain", target: t, amount: next.block - prev.block });
  if (prev.block > 0 && next.block === 0) out.push({ kind: "blockBreak", target: t });
  // Block spent with no HP behind it absorbed the whole hit. Only trustworthy
  // WITHIN one player turn: block also zeroes at end of turn, and a snapshot
  // pair spanning the enemy turn can't tell "absorbed a hit" from "expired".
  // The enemy turn's own fully-blocked case is decided in `sound/triggers.ts`,
  // which knows who actually attacked.
  if (sameRound && next.block < prev.block && next.hp === prev.hp) {
    out.push({ kind: "fullyBlocked", target: t });
  }
  if (next.hp < prev.hp) out.push({ kind: "hurt", target: t, amount: prev.hp - next.hp });
  if (next.hp > prev.hp) out.push({ kind: "heal", target: t, amount: next.hp - prev.hp });

  const prevNames = new Set(prev.powers.map((p) => p.name));
  const nextNames = new Set(next.powers.map((p) => p.name));
  // `p.amount` is the stack TOTAL in the snapshot the power is present in, which
  // is exactly the `power.Amount` the game classifies by — see
  // `EffectEvent.powerAdd`'s doc. An add reads it from `next` (where the power
  // now is), a removal from `prev` (its last known value before it went).
  for (const p of next.powers) {
    if (!prevNames.has(p.name)) out.push({ kind: "powerAdd", target: t, power: p.name, amount: p.amount });
  }
  for (const p of prev.powers) {
    if (!nextNames.has(p.name)) out.push({ kind: "powerRemove", target: t, power: p.name, amount: p.amount });
  }
}

export function combatDiff(prev: CombatState | null, next: CombatState): EffectEvent[] {
  if (!prev) return [];
  const out: EffectEvent[] = [];
  const sameRound = next.round === prev.round;

  diffCreature(
    { target: { side: "player" }, hp: prev.player.hp, block: prev.player.block, powers: prev.player_powers ?? [] },
    { target: { side: "player" }, hp: next.player.hp, block: next.player.block, powers: next.player_powers ?? [] },
    out,
    sameRound,
  );

  if (prev.osty && next.osty) {
    const wasAlive = prev.osty.alive !== false;
    const nowAlive = next.osty.alive !== false;
    if (wasAlive && nowAlive) {
      diffCreature(
        { target: { side: "osty" }, hp: prev.osty.hp ?? 0, block: prev.osty.block ?? 0, powers: [] },
        { target: { side: "osty" }, hp: next.osty.hp ?? 0, block: next.osty.block ?? 0, powers: [] },
        out,
        sameRound,
      );
    }
    if (wasAlive && !nowAlive) out.push({ kind: "death", target: { side: "osty" } });
  }

  const nextEnemies = new Map((next.enemies ?? []).map((e) => [e.index, e]));
  const prevEnemies = new Map((prev.enemies ?? []).map((e) => [e.index, e]));
  for (const e of next.enemies ?? []) {
    const pe = prevEnemies.get(e.index);
    if (!pe) continue;
    diffCreature(
      { target: { side: "enemy", index: e.index }, hp: pe.hp, block: pe.block, powers: pe.powers ?? [] },
      { target: { side: "enemy", index: e.index }, hp: e.hp, block: e.block, powers: e.powers ?? [] },
      out,
      sameRound,
    );
    if (pe.hp > 0 && e.hp <= 0) out.push({ kind: "death", target: { side: "enemy", index: e.index } });
  }
  for (const pe of prev.enemies ?? []) {
    if (!nextEnemies.has(pe.index)) out.push({ kind: "death", target: { side: "enemy", index: pe.index } });
  }
  return out;
}
