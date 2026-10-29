import type { SfxMap } from "./sfxMap";

/**
 * A power's buff/debuff valence AS RENDERED, which is a function of its
 * AMOUNT and not only of its name.
 *
 * The game asks `power.GetTypeForAmount(power.Amount)`
 * (`PowerModel.cs:462-472`), never the bare `Type`:
 *
 * ```text
 * public PowerType GetTypeForAmount(decimal customAmount)
 * {
 *     if (StackType.Equals(PowerStackType.Counter) && AllowNegative && customAmount < 0m)
 *         return PowerType.Debuff;
 *     if (!AllowNegative && Type.Equals(PowerType.Debuff) && customAmount < 0m)
 *         return PowerType.Buff;
 *     return Type;
 * }
 * ```
 *
 * So negative Strength is a DEBUFF: red panel, downward drift, debuff chime.
 * Classifying by name alone rendered it as the buff its `Type` says it is.
 *
 * **Why there is no `StackType` column.** The first arm's `Counter` check is
 * satisfiable from the `AllowNegative` set alone at v0.107.1. Four of the five
 * (`Strength`, `Dexterity`, `Focus`, `Shriek`) declare
 * `StackType => PowerStackType.Counter` outright. The fifth, `Shrink`, computes
 * it (`ShrinkPower.cs:26-35`: `Counter` when it has an applier, else `Single`),
 * but `Shrink`'s own `Type` is already `Debuff` — so its `Single` branch falls
 * past both arms to `return Type` and lands on `Debuff` anyway, the same answer
 * the `Counter` branch gives. The two paths agree, which is what lets the
 * condition collapse. A future power that is `AllowNegative`, non-`Counter` and
 * typed `Buff` would break that and needs the column added here and in
 * `build_sfx_map.py`'s `load_power_allow_negative_table`.
 */
export type PowerValence = "Buff" | "Debuff";

/**
 * `GetTypeForAmount`, transcribed. `valence` is the power's static `Type` from
 * `sfx_map.json`'s `powers` table and `allowNegative` its membership in
 * `powersAllowNegative`; `amount` is the power's TOTAL stack count at the
 * moment the event fires — `power.Amount`, which is what the C# passes — not
 * the delta that was just applied.
 *
 * `undefined` for either the valence or the amount returns the valence
 * unchanged, so an unclassified power (or an event from a producer that could
 * not resolve a total) degrades to exactly the name-only behaviour rather than
 * to a wrong colour or to nothing at all.
 */
export function powerTypeForAmount(
  valence: PowerValence | undefined,
  allowNegative: boolean,
  amount: number | undefined,
): PowerValence | undefined {
  if (amount === undefined || amount >= 0) return valence;
  if (allowNegative) return "Debuff";
  if (valence === "Debuff") return "Buff";
  return valence;
}

/**
 * The two `sfx_map.json` tables `powerTypeForAmount` needs, bound to one
 * lookup. `powersAllowNegative` is optional on `SfxMap` (a deployed bucket can
 * still be serving a map generated before it existed), and an absent list
 * reads as "nothing allows negatives" — i.e. the name-only behaviour again.
 */
export function powerValenceLookup(
  map: Pick<SfxMap, "powers" | "powersAllowNegative"> | null,
): (power: string, amount: number | undefined) => PowerValence | undefined {
  const allowNegative = new Set(map?.powersAllowNegative ?? []);
  return (power, amount) =>
    powerTypeForAmount(map?.powers[power], allowNegative.has(power), amount);
}
