import type { TargetRef } from "./types";

/** One overlay effect instance to start. */
export interface PlayedEffect {
  /** Manifest key, e.g. `"vfx_attack_slash"`. */
  effect: string;
  target: TargetRef;
  /** `VfxCmd.PlayOnCreature` (the node origin) vs `PlayOnCreatureCenter`
   *  (`NCreature.VfxSpawnPosition`), plus a THIRD anchor `vfx_block_broken`
   *  alone uses: `"badge"` is the block badge's own centre
   *  (`NHealthBar.cs:575`: `_blockContainer.GlobalPosition +
   *  _blockContainer.Size * 0.5`), not the creature at all —
   *  `block_spark_vfx` anchors to the creature (`"center"`,
   *  `NBlockSparkVfx.cs:86`'s `VfxSpawnPosition`), so the two block effects
   *  genuinely differ and must not be unified. `AttackCommand` distinguishes
   *  `origin`/`center` per effect, so the manifest consumer must too. */
  anchor: "origin" | "center" | "badge" | "point";
  /** Where a `anchor: "point"` instance spawns, in VIEWPORT px (the same
   *  coordinates `getBoundingClientRect` reports), or `null` when the caller
   *  could not resolve one — nothing is drawn for a point play without a
   *  point, the same way a `badge` play draws nothing when the badge is not
   *  mounted.
   *
   *  Every other anchor derives its spawn from the target creature's own DOM
   *  rect. `"point"` exists because one effect cannot: the Kaiser Crab's
   *  death explosion is spawned by `NKaiserCrabBossVfx.OnClawLExplode` at
   *  `%LeftArmExplosionPosition`, a `SpineBoneNode` that FOLLOWS the rig's
   *  `claw_upper` bone. At the instant it fires — `claw_explode_l`, t=1.53s
   *  of `left/die` — that bone is nowhere near where the arm's chrome sits:
   *  the arm's `TargetRef` resolves to a spriteless overlay pinned to the
   *  ground line, while the claw is up in the air mid-animation. Only the
   *  component drawing the rig can say where the bone is, so it resolves the
   *  point and hands it through here.
   *
   *  Viewport rather than arena px so the sink converts it exactly as it
   *  converts a rect — one subtraction of the arena's own rect, in one
   *  place. */
  point?: { x: number; y: number } | null;
  /** The binding table's tint for this event (`EffectPick.tint`), `#RRGGBB`
   *  or `null` for none. Only a particle composite consumes it today (design
   *  spec §1.4/§5, "applies the binding tint over the emitter's
   *  self_modulate") — flipbook/Spine draws ignore it, same as before this
   *  field existed. */
  tint: string | null;
  /** The texture a `kind: "sprite_shader"` instance draws — `power_flash_vfx`
   *  alone consumes this. The manifest carries no texture for that effect
   *  (`NPowerFlashVfx.cs:94`: `_sprite.Texture = _power.BigIcon`, assigned at
   *  runtime, not authored in the scene), so the caller resolves the
   *  flashing power's own icon URL and hands it through here. `undefined`/
   *  `null` for every other effect kind, and for a `powerFlash` whose power
   *  has no icon on record — nothing draws for an instance with no texture,
   *  same as a flipbook/particle texture still in flight. */
  iconUrl?: string | null;
  /** The two per-creature facts `vfx_monster_death`'s debris burst needs and
   *  the manifest cannot carry, or `null` for every other effect. Present for
   *  the same reason `iconUrl` is: the game resolves both at runtime from the
   *  dying creature's measured bounds, which only the caller knows.
   *
   *  - `emissionRadius` — the burst's `emission_sphere_radius` in scene px.
   *    `NMonsterDeathVfx.cs:231` overwrites the scene's placeholder with
   *    `node.Size.Y / 4f`, so the manifest carries only the RULE
   *    (`DissolveEffect.emissionRadiusPerCreatureExtent`) and
   *    `extract_vfx.py` blanks the placeholder itself to 0. Resolved through
   *    `effects/dissolve.ts`'s `deathBurstRadiusFor`.
   *  - `lifetime` — how long the burst lives, in seconds. The C# frees the
   *    WHOLE death node when the threshold tween finishes
   *    (`NMonsterDeathVfx.cs:240-241`), and that tween is per-creature, so a
   *    small monster's burst is cut off before its 1.8 s particles expire.
   *    The manifest's flat 2.5 s is the ceiling; `dissolveSecondsFor` narrows
   *    it, exactly as `Board`'s `dissolveEffectFor` already does for the
   *    dissolve sprite.
   *
   *  Absent (or `null`) for a dissolve play means the creature has no
   *  measurable box, and nothing is drawn — the honest outcome, since the
   *  blanked manifest radius would put every particle on one point. */
  dissolve?: { emissionRadius: number; lifetime: number } | null;
}

/**
 * Where overlay effects go.
 *
 * This is the seam between tested and untested code: everything deciding WHAT
 * plays and WHERE is asserted against a recording fake, and only `VfxLayer`'s
 * WebGL calls are unexercised (jsdom has no GL context).
 */
export interface EffectSink {
  play(p: PlayedEffect): void;
}

/** Used before the overlay mounts, and in tests that do not care. */
export const nullSink: EffectSink = { play: () => {} };
