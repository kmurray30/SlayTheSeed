import type { CombatState } from "../api";
import type { TargetRef } from "./types";

/**
 * Resolve a combat-log row's creature to a board `TargetRef`.
 *
 * `states` is searched IN ORDER, so callers pass `[next, prev]`: an enemy
 * killed by the hit being resolved is already absent from `next.enemies`
 * (`rust_game.py` skips dead enemies while keeping the encounter-slot index),
 * but is still in `prev`, which is the slot Board's `dying` overlay draws.
 *
 * Returns `null` when the uid is on no board — Osty (which carries no uid)
 * and creatures already torn down. Those are covered by the snapshot
 * reconciliation in `actionStream`, so a `null` here is a routing decision,
 * not a dropped event.
 */
export function targetForUid(
  uid: number | null | undefined,
  side: string | null,
  states: (CombatState | null)[],
): TargetRef | null {
  if (side === "player") return { side: "player" };
  if (uid == null) return null;
  for (const s of states) {
    const e = (s?.enemies ?? []).find((x) => x.uid === uid);
    if (e) return { side: "enemy", index: e.index };
  }
  return null;
}
