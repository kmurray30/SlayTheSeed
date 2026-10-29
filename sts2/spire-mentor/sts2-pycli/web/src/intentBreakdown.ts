/** Turning an intent's engine-supplied modifier rows into something readable.
 *
 *  Bug report `wb-4eae3bad6ebd57f8`: a Torch Head Amalgam telegraphing 12x3
 *  kept telegraphing 12x3 after an Uppercut applied Weak to it, and the board
 *  offered no way to see that the x0.75 was ALREADY in the 12 (Weak was on at
 *  2 stacks from an earlier Shockwave; Weak is a duration counter, so a third
 *  stack cannot move the number). The engine was right and the screen was
 *  unreadable, which is its own defect.
 *
 *  This is a WORKBENCH affordance, not a port of a game one: `NIntent` paints
 *  the numeral plain white and `AttackIntent` has no hover tip, so the real
 *  game shows exactly the same bare number. The workbench is where you ask
 *  *why* a board reads what it reads, which is why it gets the breakdown and
 *  the game does not.
 *
 *  Nothing here computes damage. The rows arrive already reconciled by the
 *  engine (`hooks::damage_modifier_breakdown` folds anything it cannot name
 *  into a trailing `other` row), so the printed arithmetic always lands on the
 *  number the engine actually telegraphed.
 */

export type IntentModifier = {
  /** Engine registry id — `WEAK`, `STRENGTH`, `BURNING_BLOOD`. */
  name: string;
  kind: "power" | "relic" | "other" | string;
  /** Whose listener: `"enemy"` = the attacker's, `"player"` = yours. */
  side?: "enemy" | "player" | string;
  /** Display title + tray icon, resolved server-side (see server.py). */
  title?: string;
  image?: string | null;
  /** The channel this listener moved. Absent = it did not move that one. */
  add?: number;
  mul?: [number, number];
  cap?: number;
};

export type IntentLike = {
  damage?: number;
  base_damage?: number;
  hits?: number;
  modifiers?: IntentModifier[];
};

function label(m: IntentModifier): string {
  const name = m.title || m.name;
  // "(you)" / "(attacker)" is the distinction the bug report needed: the same
  // Vulnerable icon means "it takes more" on the enemy and "you take more" on
  // the player, and only one of those is in this number.
  const whose = m.side === "player" ? " (you)" : m.side === "enemy" ? " (attacker)" : "";
  return `${name}${whose}`;
}

/** `[3, 4]` -> `"x3/4"`; an integer ratio collapses to `"x2"`. */
export function formatMul([num, den]: [number, number]): string {
  if (den !== 0 && num % den === 0) return `×${num / den}`;
  return `×${num}/${den}`;
}

/** Did the modifiers push the number down, up, or nowhere? */
export function intentDirection(intent: IntentLike): "down" | "up" | null {
  const { damage, base_damage: base } = intent;
  if (damage == null || base == null || damage === base) return null;
  return damage < base ? "down" : "up";
}

/** A multi-line `title=` string spelling out the telegraph's arithmetic, or
 *  `undefined` when there is nothing to explain (no modifiers reached us, or
 *  the backend predates them). */
export function describeIntent(intent: IntentLike): string | undefined {
  const { damage, base_damage: base, modifiers } = intent;
  if (damage == null || base == null || !modifiers || modifiers.length === 0) return undefined;
  // Pipeline order, not listener order: the engine walks the player's powers
  // before each enemy's, but it APPLIES additive -> multiplicative -> cap, and
  // a reader following the sum needs the second order.
  const lines = [`${base} base`];
  for (const m of modifiers) {
    if (m.add) lines.push(`${m.add > 0 ? "+" : ""}${m.add} ${label(m)}`);
  }
  for (const m of modifiers) {
    if (m.mul) lines.push(`${formatMul(m.mul)} ${label(m)}`);
  }
  for (const m of modifiers) {
    if (m.cap != null) lines.push(`capped at ${m.cap} ${label(m)}`);
  }
  const per = intent.hits && intent.hits > 1 ? " per hit" : "";
  return `${damage} damage${per}\n${lines.join("\n")}`;
}
