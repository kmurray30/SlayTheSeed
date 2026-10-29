// Pure helpers for choosing which Spine clip to play. `has(name)` reports
// whether the rig contains a clip of that name (SpineSprite knows this).

export function firstAvailable(candidates: string[], has: (n: string) => boolean): string | undefined {
  return candidates.find(has);
}

export interface AttackShape {
  hits: number;
  totalDamage: number;
}

// Map an attack's shape to an ordered candidate list, then pick the first the
// rig actually has. Heavy hits and multi-hits get their flavor clip when the
// rig provides one; otherwise everything degrades to "attack".
export function pickAttackClip(has: (n: string) => boolean, shape: AttackShape): string | undefined {
  const candidates: string[] = [];
  if (shape.hits >= 3) candidates.push("attack_multi", "attack_triple");
  else if (shape.hits === 2) candidates.push("attack_double");
  if (shape.totalDamage >= 20) candidates.push("attack_heavy");
  candidates.push("attack");
  return firstAvailable(candidates, has);
}
