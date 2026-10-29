/**
 * Which Kaiser Crab rig VFX each of the boss's SPINE EVENTS drives.
 *
 * The crab is the one creature in the game whose effects are fired by its own
 * animation rather than by anything in the model layer. `NKaiserCrabBossVfx`
 * listens on the rig and dispatches ten authored events; `KaiserCrab.tsx`
 * surfaces them through `onRigEvent`, and this table says what to do with one.
 *
 * Kept as data, and separate from the component, so the mapping is testable
 * without a WebGL context — the same seam `bindings.ts` draws for the
 * card/move -> VFX table.
 *
 * ONE event is wired today (GH #642). The other nine are real and currently
 * dropped; each needs its own emitter extracted before it can be added, and
 * six of them are already authored in `kaiser_crab_boss_setup.tscn` as
 * `GPUParticles2D` nodes hanging off spine slots:
 *
 * | event                              | game does                          |
 * |------------------------------------|------------------------------------|
 * | `charge_steam_start` / `_end`      | `SteamParticles1..3` on `thruster_attach` |
 * | `rocket_thrust_start` / `_end`     | `SmokeParticles` + `SparkParticles`      |
 * | `regen_splats_start` / `_end`      | `_regenSplatParticles`                   |
 * | `death_spit_start` / `_end`        | `SpittleParticles` on `SpittleSlot`      |
 * | `plow_chunks_start` / `_end`       | `_plowChunkParticles`                    |
 * | `rocket_throttle`                  | audio only (no VFX listener)             |
 *
 * Adding one is a row here plus its emitter in `extract_vfx.py`.
 */
export interface CrabRigVfx {
  /** Manifest effect key to play. */
  effect: string;
  /** Rig bone the spawn point follows, read at the instant the event fires. */
  bone: string;
}

/**
 * The VFX a crab rig event spawns, or `null` for an event nothing draws yet.
 *
 * `claw_explode_l` is the Crusher's death explosion. C#:
 * `NKaiserCrabBossVfx.OnClawLExplode` (line 387) calls
 * `VfxCmd.PlayVfx(_leftArmExplosionPosition.GlobalPosition,
 * "vfx/monsters/kaiser_crab_boss_explosion", ...)`, and
 * `%LeftArmExplosionPosition` is a `SpineBoneNode` with
 * `bone_name = "claw_upper"` — it FOLLOWS the bone rather than sitting at a
 * fixed point, which matters because the event fires 1.53 s into `left/die`
 * with the claw high in the air. (The scene also authors a `position` on that
 * node; it is an editor-time snapshot of the bone's setup pose — rotation
 * 0.961 rad against the bone's own -0.961 in Spine's Y-up frame — not a
 * placement to honour.)
 *
 * Note this is the CRUSHER's death, not the Rocket's: the rig has no
 * `right/die` counterpart event, and `Crusher.deathVfxPath` names the same
 * scene (though nothing reads that const).
 */
export function crabRigEventVfx(name: string): CrabRigVfx | null {
  if (name === "claw_explode_l") {
    return { effect: "vfx_kaiser_crab_boss_explosion", bone: "claw_upper" };
  }
  return null;
}
