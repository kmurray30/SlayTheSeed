/**
 * Monster-variant → Spine SKIN selection, mirroring the game's per-monster
 * `SetupSkins` overrides (decomp `MegaCrit.Sts2.Core.Models.Monsters/*.cs`).
 *
 * Several monster VARIANTS share one rig with their base monster (see the
 * alias table in `python/workbench/assets.py`) and get their distinct look
 * from a NAMED skin on that shared skeleton — e.g. the four Bowlbugs are all
 * the `bowlbug` rig wearing "cocoon" / "goop" / "rock" / "web". The wire enemy
 * carries only the display `name` (the rig path arrives already aliased), so
 * the skin decision re-derives the variant slug from the name with the same
 * slugify rules the backend uses.
 *
 * Deterministic selections (decomp file: skin):
 *  - BowlbugEgg.cs        → "cocoon"  (plus an "egg1" skin on a SECOND
 *    skeleton — a separate Godot egg prop node the workbench doesn't render;
 *    known gap, the cocooned bowl is the recognizable part)
 *  - BowlbugNectar.cs     → "goop"
 *  - BowlbugRock.cs       → "rock"
 *  - BowlbugSilk.cs       → "web"
 *  - BattleFriendV1/2/3.cs→ "v1"/"v2"/"v3"   (battleworn_dummy rig)
 *  - CalcifiedCultist.cs  → "coral"          (cultists rig)
 *  - DampCultist.cs       → "slug"           (cultists rig)
 *  - PhantasmalGardener.cs→ "tall" when the creature's SlotName is
 *    "first"/"third", else "short" — approximated here by the wire enemy
 *    index (even → tall, odd → short), matching spawn order.
 *  - Byrdpip.cs / PaelsLegion.cs (pet monsters) → the relic picks a random
 *    saved skin per run (Relics/Byrdpip.cs SkinOptions, Relics/PaelsLegion.cs
 *    SkinOptions); that choice is NOT on the wire, so we show each pet's
 *    SkinOptions[0] default ("version1" / "eyes").
 *
 * Monsters whose SetupSkins picks RANDOM cosmetic skins at spawn (Toadpole,
 * TwoTailedRat, ScrollOfBiting, CubexConstruct, KinFollower, ToughEgg) are
 * deliberately NOT mapped: the choice is unobservable and SpineSprite's
 * composeSetupSkin already fills their setup slots deterministically.
 */

const MONSTER_SKINS: Record<string, string> = {
  bowlbug_egg: "cocoon",
  bowlbug_nectar: "goop",
  bowlbug_rock: "rock",
  bowlbug_silk: "web",
  battle_friend_v1_0: "v1",
  battle_friend_v2_0: "v2",
  battle_friend_v3_0: "v3",
  calcified_cultist: "coral",
  damp_cultist: "slug",
  byrdpip: "version1",
  paels_legion: "eyes",
};

/** Mirror of `workbench/assets.py slugify`: lowercase, drop apostrophes,
 *  collapse non-alphanumerics to "_", trim "_", drop a leading "the_". */
export function slugifyMonster(name: string): string {
  let s = name.trim().toLowerCase().replace(/['’]/g, "");
  s = s.replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
  if (s.startsWith("the_")) s = s.slice(4);
  return s;
}

/**
 * The Spine skin the game would wear for this enemy, or undefined when the
 * rig's default/composed look is already correct. `index` is the wire enemy
 * index (used only for the Phantasmal Gardener's slot-based tall/short pick).
 */
export function monsterSkin(name: string, index?: number): string | undefined {
  const slug = slugifyMonster(name);
  if (slug === "phantasmal_gardener") {
    return index != null && index % 2 === 1 ? "short" : "tall";
  }
  return MONSTER_SKINS[slug];
}
