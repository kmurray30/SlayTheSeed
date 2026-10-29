import { assetUrl } from "../api";

export interface FlipbookEffect {
  kind: "flipbook";
  frames: string[];
  fps: number;
  loop: boolean;
  scale: number;
  randomizeRotation: boolean;
  rotationRange: [number, number];
}

export interface SpineEffect {
  kind: "spine";
  /** Rig base path; the loader appends `.skel` / `.atlas` / `.png`. */
  rig: string;
  scale: number;
  /** Clip name to play on the rig, e.g. `"adrenaline"`, or `null` when the
   *  scene authors none — `vfx_scene.py`'s `SpineVfxSpec.clip` is
   *  `str | None`, and every committed entry happening to carry one does not
   *  make the field non-nullable. */
  clip: string | null;
  /** Particle emitters parented to slots on this rig, each started by one of
   *  the rig's own animation events (GH #669). ABSENT — not empty — for a
   *  scene with none, which is every spine effect but
   *  `vfx_kaiser_crab_boss_explosion`. */
  emitters?: SpineSlotEmitter[];
}

/** Per-particle-system curve/LUT + physics knobs a `ParticleEmitter` reads
 *  (Godot `ParticleProcessMaterial`, extracted verbatim by `extract_vfx.py`).
 *  Every `*Curve`/`colorRamp` field is either a `vfx_particles/<hash>.json`
 *  LUT path or `null` (no curve authored) — never any other string. */
export interface ParticleEmitterProcess {
  alphaCurve: string | null;
  /** Inline baked RGBA gradient (256 stops), NOT a path — unlike every
   *  other `*Ramp`/`*Curve` field here, which name a LUT file. */
  colorInitialRamp: [number, number, number, number][] | null;
  colorRamp: string | null;
  dampingCurve: string | null;
  dampingMax: number;
  dampingMin: number;
  direction: [number, number, number];
  flatness: number;
  gravity: [number, number, number];
  initialVelocityMax: number;
  initialVelocityMin: number;
  lifetimeRandomness: number;
  /** Godot's `PARAM_ANIM_OFFSET`, drawn per particle at spawn as
   *  `mix(min, max, rand)`. The flipbook shaders read it as
   *  `INSTANCE_CUSTOM.z` to give each particle its OWN starting frame of the
   *  sheet; left at zero, every particle of an emitter animates in lockstep.
   *  Authored on 190 emitters across the pck's vfx scenes. */
  animOffsetMin: number;
  animOffsetMax: number;
  /** `PARAM_ANIM_SPEED`, the OTHER half of the flipbook phase. The shader
   *  builds `CUSTOM.z = animation_offset + lifetime_percent * animation_speed`
   *  (`particle_process_material.cpp:1147`), so the offset alone picks a
   *  starting frame and never advances it — at speed 0 (the Godot default,
   *  and most emitters here) that is correct and the sheet is a static pick;
   *  where it is authored, the particle plays through the sheet. */
  animSpeedMin: number;
  animSpeedMax: number;
  /** Multiplies the speed over life — `parameters.animation_speed *=
   *  texture(animation_speed_curve, ...)`. Note the asymmetry with the offset
   *  curve, which the same function ADDS (`+=`). */
  animSpeedCurve: string | null;
  /** `PARAM_ANGLE`, the particle's initial rotation, in DEGREES
   *  (`particle_process_material.cpp:2222`'s hint ends `,degrees`). Several
   *  emitters author +/-1000, i.e. "any rotation at all"; block spark and the
   *  goopy/wormy impacts author a clean 0-360. */
  angleMin: number;
  angleMax: number;
  /** MULTIPLIES the initial angle over life (`base_angle *= texture(...)`,
   *  `:1076`) — it does not add to it. */
  angleCurve: string | null;
  /** Godot's `EmissionShape`, verbatim: 0 Point, 1 Sphere, 2 Sphere Surface,
   *  3 Box, 4 Points, 5 Directed Points, 6 Ring. The shipped composites use
   *  1, 2, 3 and 6 — every particle used to spawn from a point regardless. */
  emissionShape: number;
  emissionSphereRadius: number;
  emissionBoxExtents: [number, number, number];
  emissionRingAxis: [number, number, number];
  emissionRingHeight: number;
  emissionRingRadius: number;
  emissionRingInnerRadius: number;
  emissionRingConeAngle: number;
  /** Applied to EVERY shape including Point (`pos * scale + offset`), so a
   *  non-default pair is not a no-op even for a point emitter. */
  emissionShapeOffset: [number, number, number];
  emissionShapeScale: [number, number, number];
  /** `PARAM_ANGULAR_VELOCITY`, the spin, in DEGREES PER SECOND. */
  angularVelocityMin: number;
  angularVelocityMax: number;
  /** Multiplies the whole accumulated spin term, sampled once at the current
   *  lifetime — `base_angle += CUSTOM.y * LIFETIME * omega * curve(t)`
   *  (`:1079`). Deliberately NOT an integral of a varying omega; porting it as
   *  one would drift away from the game. */
  angularVelocityCurve: string | null;
  particleFlagAlignY: boolean;
  particleFlagDampingAsFriction: boolean;
  particleFlagDisableZ: boolean;
  particleFlagRotateY: boolean;
  radialVelocityCurve: string | null;
  radialVelocityMax: number;
  radialVelocityMin: number;
  scaleCurve: string | null;
  scaleMax: number;
  scaleMin: number;
  scaleOverVelocityCurve: string | null;
  scaleOverVelocityMax: number;
  spread: number;
  turbulenceEnabled: boolean;
  turbulenceInfluenceMax: number;
  turbulenceInfluenceMin: number;
  turbulenceInfluenceOverLifeCurve: string | null;
  turbulenceInitialDisplacementMax: number;
  turbulenceInitialDisplacementMin: number;
  turbulenceNoiseScale: number;
  turbulenceNoiseSpeed: [number, number, number];
  turbulenceNoiseSpeedRandom: number;
  turbulenceNoiseStrength: number;
}

/** A Godot `ShaderMaterial` (`materials/vfx/....tres`), reduced to the
 *  uniforms this port reads.
 *
 *  This is where a particle's COLOUR comes from: every shader in the family
 *  does `texture(lut, tex.rr) * vertex_color`, using the grayscale texture as
 *  an index into a 1-D gradient rather than as a colour. The per-emitter
 *  `ParticleEmitterProcess` carries none of it — a port that reads only the
 *  scene renders 37 of the 48 material-bearing emitters in the wrong colour.
 */
export interface ParticleMaterial {
  /** Source `.tres` res-path, kept for provenance and coverage tests. */
  path: string;
  /** `.gdshader` basename, e.g. `"vfx_flipbook_shader"`. */
  shader: string;
  /** Baked RGBA colourisation LUT (`vfx_particles/<hash>.json`), or `null`
   *  for the one shader in use that declares no `lut` uniform at all
   *  (`vfx_ring_polar_shader`, which colours straight from the vertex). */
  lut: string | null;
  /** Sprite-sheet frame count, or `null` when this material is not a
   *  flipbook. Present on 24 of the 48 material-bearing emitters. */
  frameCount: number | null;
  /** Sheet layout in frames, `[columns, rows]`. */
  flipbookSize: [number, number] | null;
  /** Baked scalar curve mapping lifetime fraction to flipbook progress. */
  flipbookCurve: string | null;
  /** `mix()`-gated shader extras. Carried so the gap stays visible and
   *  bounded rather than merely unmentioned: measured across every material
   *  in use, `useErosion` is true for 0 emitters and `useHueShift` for 3. */
  useErosion: boolean;
  useHueShift: boolean;
}

/** One emitter in a `ParticlesEffect`. Composites carry several — the 13
 *  extracted particle effects range from 1 to 8 emitters each. */
export interface ParticleEmitter {
  name: string;
  /** Sprite/flipbook texture, e.g. `"vfx/common/common_glow.png"` or
   *  `"animations/vfx/vfx_sandy_impact/vfx_ash_dust.png"` — the extractor
   *  names both `vfx/` and `animations/` roots under `images_root`. */
  texture: string;
  /** Godot particle material, or `null` for a plain textured quad with no
   *  material override. */
  material: ParticleMaterial | null;
  amount: number;
  explosiveness: number;
  fixedFps: number;
  lifetime: number;
  localCoords: boolean;
  offset: [number, number];
  oneShot: boolean;
  randomness: number;
  rotation: number;
  /** RGBA tint multiplier. */
  selfModulate: [number, number, number, number];
  startDelay: number;
  zIndex: number;
  /** Whether this emitter is a DESCENDANT of the node `ParticlesEffect
   *  .pivotScale` / `.mirrorX` names — i.e. whether that bespoke transform
   *  applies to it. Resolved from the real scene graph at extraction time,
   *  because the manifest carries no parent/child edge and inferring it from
   *  names over-matches: `vfx_spore_impact`'s pivot node has exactly one
   *  child, but the composite also holds a root-level `vfx_poof`. */
  underPivotScale: boolean;
  underMirrorX: boolean;
  /** Whether this emitter is a `CPUParticles2D` rather than a
   *  `GPUParticles2D`. The two nodes take the same property names and the
   *  extractor resolves both into this one shape, but they do NOT compute the
   *  same particles from it: the emission sphere alone is three draws of a 3D
   *  ball on the GPU (`particle_process_material.cpp:645-648`) against two
   *  draws of a flat, radius-uniform disc on the CPU
   *  (`cpu_particles_2d.cpp:893-897`), and `CPUParticles2D` has no
   *  `emission_shape_scale`/`_offset` at all. So this is data a renderer must
   *  branch on, not provenance — `simulate.ts`'s `calculateInitialPosition`
   *  does. `vfx_monster_death`'s debris burst is the pck's one in-scope
   *  `CPUParticles2D`, so this is `true` exactly once. */
  cpu: boolean;
  /** The Spine SLOT this emitter is parented to (`SpineSlotNode.slot_name`),
   *  or `null` for an emitter that hangs off an ordinary `Node2D`. Only a
   *  `SpineEffect`'s own emitters ever carry one: a slot-parented emitter
   *  FOLLOWS the rig's animated slot rather than sitting at a fixed offset
   *  from the effect root, so the renderer reads the slot's world position
   *  every frame instead of drawing at `(x, y)`. `null` for all 14
   *  `ParticlesEffect` composites. GH #669. */
  slot: string | null;
  /** `CanvasItemMaterial.blend_mode == 1` (Add) on the emitter's own material
   *  sub-resource. `material` above models `ShaderMaterial` `.tres` files
   *  only, so a blend-mode-only `CanvasItemMaterial` arrives with
   *  `material: null` and this is the only place its blend survives — for
   *  `vfx_kaiser_crab_boss_explosion`'s embers it is the difference between
   *  glowing sparks and grey dots. */
  blendAdd: boolean;
  /** `GPUParticles2D.trail_enabled` / `.trail_lifetime`. Godot draws a
   *  trail-enabled particle as a ribbon through the positions it held over the
   *  last `trailLifetime` seconds, not as a single quad. `false`/`0.3` (the
   *  Godot default) for all 14 composites; `vfx_kaiser_crab_boss_explosion`'s
   *  ember burst is the one emitter in scope that enables it, and there the
   *  ribbon IS the effect — the quad alone is a 24x24 dot at 0.1-0.4 scale
   *  under a 0.25 `SpineSprite`, under two screen pixels, while the ribbon its
   *  2500-5000 initial velocity sweeps out is a 30-60 px streak. GH #669. */
  trailEnabled: boolean;
  trailLifetime: number;
  process: ParticleEmitterProcess;
}

/** A {@link ParticleEmitter} on a {@link SpineEffect}: parented to a slot on
 *  that effect's OWN rig, and started by that rig's own animation event
 *  rather than when the effect begins.
 *
 *  `kaiser_crab_boss_explosion.tscn` is the shape:
 *  `NKaiserCrabBossExplosionVfx._Ready` sets `Emitting = false` /
 *  `OneShot = true` on `ExplosionSlot/ExplosionParticles`, and restarts it
 *  when the rig raises `left_embers_start` at t=0.100s of the 0.67s
 *  `animation` clip. Neither fact is in the `.tscn` — both come from the
 *  effect's C# script, via the extractor's
 *  `SPINE_SLOT_EMITTER_SCRIPT_FACTS`. GH #669. */
export interface SpineSlotEmitter extends ParticleEmitter {
  slot: string;
  /** Spine event name on the effect's own rig that restarts this emitter. */
  event: string;
}

/** An `NVfxParticleSystem` / `N*ImpactVfx` composite of `GPUParticles2D`
 *  emitters (`vfx_attack_blunt`, `vfx_spore_impact`, etc.), extracted by
 *  `extract_vfx.py`'s particle-scene walker. Distinct from `FlipbookEffect`
 *  (a baked PNG sequence) and `SpineEffect` (a skeletal rig) — this is a
 *  *scene* the renderer instantiates, not a single asset. The pck's one
 *  in-scope `CPUParticles2D` emitter belongs to `DissolveEffect`, not here. */
export interface ParticlesEffect {
  kind: "particles";
  lifetime: number;
  emitters: ParticleEmitter[];
  /** Scene node name to mirror horizontally, or `null` — an authoring
   *  concept from the extracted `.tscn`, not a resolvable asset path. */
  mirrorX: string | null;
  /** `[node name, scaleX, scaleY]` pivot override, or `null`. Like
   *  `mirrorX`, the first element is a scene node name, not a path. */
  pivotScale: [string, number, number] | null;
  tinted: boolean;
}

/** A sprite (or several) driven by baked `AnimationPlayer` transform tracks —
 *  `vfx_block_broken`'s two halves thrown apart on position and rotation.
 *  Distinct from `kind: "flipbook"`, which swaps TEXTURES on a timer and
 *  never moves. */
export interface TransformAnimEffect {
  kind: "transform_anim";
  lifetime: number;
  sprites: {
    name: string;
    texture: string;
    /** Baked 256-sample LUT paths, each baked over the effect's whole
     *  `lifetime` (not the track's own key span — see `anim_track.bake_track`),
     *  so all three sample at the same `elapsed / lifetime`. A null track is
     *  a channel the animation never touches — hold the sprite's authored
     *  value (`rest*` below), do not default it to zero. */
    positionX: string | null;
    positionY: string | null;
    rotation: string | null;
    /** The sprite node's OWN authored transform in the scene (Godot's node
     *  defaults, `0`, where the scene omits it) — what a null channel above
     *  holds for the whole effect. Carried here because nothing else knows
     *  it: a LUT path's absence says a channel is untouched, never what it
     *  is untouched AT. Position is in scene px, rotation in radians, both
     *  in the same convention as the LUT values they stand in for. */
    restPositionX: number;
    restPositionY: number;
    restRotation: number;
  }[];
}

/** The dying creature, snapshotted into a `SubViewport` and eaten away by
 *  `shaders/dissolve.gdshader`, plus a `CPUParticles2D` debris burst
 *  (`vfx_monster_death`). Both noise fields the shader samples are procedural
 *  `FastNoiseLite`, so `extract_vfx.py` BAKES them to PNGs rather than
 *  decoding stored art. */
export interface DissolveEffect {
  kind: "dissolve";
  /** `.gdshader` basename, e.g. `"dissolve"`. */
  shader: string;
  /** The shader's own `render_mode` blend token, verbatim — `dissolve` is
   *  `"blend_premul_alpha"`, whose colour is already multiplied by its alpha.
   *  Compositing it as ordinary alpha mix double-darkens the fade.
   *
   *  Scope is the DISSOLVE SPRITE ONLY — the exact opposite of
   *  `modulateAlpha` below, and the reason both say so. A `CanvasItem`'s
   *  `material` does not descend to its children (only an explicit
   *  `use_parent_material` opts in, and the debris node does not set it);
   *  `modulate` does. The debris carries its own `CanvasItemMaterial`, which
   *  this extraction does not model — see `_CPU_PARAMS_NOT_MODELLED` in
   *  `particle_scene.py` — so draw the burst at the default alpha-mix blend
   *  rather than inheriting this token. */
  blendMode: string;
  /** Baked 512x512 grayscale noise the shader samples with `repeat_enable`.
   *  A power of two on purpose: WebGL1 cannot REPEAT-wrap an NPOT texture. */
  gradient1: string;
  gradient2: string;
  /** The scene's authored alpha on the `Visual` sprite
   *  (`modulate = Color(1, 1, 1, 0.466667)`).
   *
   *  It multiplies the WHOLE SUBTREE, not just the dissolving sprite. Godot's
   *  `CanvasItem.modulate` propagates to a node's children; `self_modulate` is
   *  the one that does not. The debris `CPUParticles2D` is declared
   *  `parent="Visual"` in `vfx_monster_death.tscn`, so the game draws the
   *  burst at this alpha too.
   *
   *  So: multiply BOTH the dissolve sprite and every debris particle by this.
   *  Applying it to the sprite alone renders the burst ~2x too bright. The
   *  emitter's own `selfModulate` stays `[1, 1, 1, 1]` — the product is
   *  deliberately NOT baked into it, because `selfModulate` is extracted
   *  verbatim from the node and baking a parent's value into a child's field
   *  would make that field a lie for every other consumer. */
  modulateAlpha: number;
  /** The `threshold` uniform's endpoints. The scene authors 1.0; the script
   *  tweens it to 0 (`NMonsterDeathVfx.cs:239`). */
  thresholdFrom: number;
  thresholdTo: number;
  /** Godot `Tween` enums for that tween, raw: `EaseType` 1 = EASE_OUT,
   *  `TransitionType` 1 = TRANS_SINE. */
  dissolveEase: number;
  dissolveTrans: number;
  /** The dissolve's CEILING in seconds, not a constant duration.
   *  `NMonsterDeathVfx.cs:235` computes
   *  `min(viewportSize.x / (0.1 * screenWidth) * 2.5, 2.5)`, where the
   *  SubViewport is sized from the creature's measured bounds — so the full
   *  2.5s is reached by any creature at least ~5% of the screen width, and a
   *  smaller one dissolves proportionally faster.
   *
   *  The extractor cannot resolve that — the bounds are the client's, not the
   *  pck's — so the ceiling is what ships here and `dissolve.ts`'s
   *  `dissolveSecondsFor` applies the rest. Nothing should read this field
   *  straight for a creature that has one: `Board.tsx`'s `dissolveEffectFor`
   *  hands every dying rig a copy of this entry with its own value in place. */
  dissolveSeconds: number;
  /** The effect's own on-screen life, which IS the dissolve tween's:
   *  `PlayVfxInternal` awaits that tween and then frees the node
   *  (`NMonsterDeathVfx.cs:240-241`), so nothing else decides when this ends.
   *
   *  Deliberately NOT the debris emitter's 1.8s `lifetime` — that is a
   *  per-particle lifespan and expires first; the burst finishing early does
   *  not end the fade. Inherits `dissolveSeconds`' caveat above: this is the
   *  ceiling, and a small creature's real value is shorter. */
  lifetime: number;
  /** The debris burst. One emitter, and a `CPUParticles2D` rather than the
   *  `GPUParticles2D` every `ParticlesEffect` carries — same parameter set,
   *  extracted to the same shape. */
  emitters: ParticleEmitter[];
  /** The debris emission sphere's radius as a FRACTION of the creature's
   *  larger on-screen extent. Not extracted from the scene: the scene's
   *  `emission_sphere_radius = 99.23` is an authoring placeholder that
   *  `NMonsterDeathVfx.cs:231` overwrites with `viewportSize.y / 4` before
   *  the burst emits, and that viewport is `2n` square over the creature's
   *  bounds — so the real radius is `n / 2`. Baking 99.23 would pin every
   *  creature, large or small, to one wrong radius. */
  emissionRadiusPerCreatureExtent: number;
}

/** One tweener inside a `SpriteShaderEffect`'s tween. */
export interface SpriteShaderTweener {
  /** `"scale"` or `"modulateAlpha"`. */
  property: string;
  to: number;
  duration: number;
  /** Godot `Tween` enums, raw: `EaseType` 0 = EASE_IN, 1 = EASE_OUT;
   *  `TransitionType` 1 = TRANS_SINE. `null` means the game calls neither
   *  `SetEase` nor `SetTrans` for this tweener, so the `Tween`'s own default
   *  applies — it does NOT mean linear. */
  ease: number | null;
  trans: number | null;
}

/** A single `Sprite2D` under a shader, animated entirely by a `Tween` the C#
 *  script builds — `vfx_power_flash_vfx`, the flash behind a power icon.
 *
 *  Distinct from `FlipbookEffect`: there are no frames, and the texture is
 *  assigned at runtime (`_sprite.Texture = _power.BigIcon`), so the manifest
 *  names none. Distinct from `TransformAnimEffect`: the motion is not in the
 *  scene at all — nothing in `power_flash_vfx.tscn` describes it — so `tween`
 *  is transcribed from `NPowerFlashVfx.cs` rather than extracted. */
export interface SpriteShaderEffect {
  kind: "sprite_shader";
  /** `.gdshader` basename, e.g. `"power_flash_vfx"`. */
  shader: string;
  /** The shader's own `render_mode` blend token, verbatim. The power flash is
   *  `"blend_add"`: composited as ordinary alpha mix it draws dark instead of
   *  glowing. */
  blendMode: string;
  /** The `strength` uniform. The shader declares `3.0`; the scene overrides it
   *  to `1.0`, and the scene is what ships. */
  strength: number;
  /** The sprite's authored starting scale (uniform). */
  scale: number;
  /** The alpha the sprite actually RENDERS at — 1.0. The scene authors
   *  `Color(1, 1, 1, 0.65098)`, but `NPowerFlashVfx.cs:95` sets
   *  `Modulate = Colors.White` before the method's first `await`, so no
   *  rendered frame ever shows the scene's value. */
  modulateAlpha: number;
  /** Outer array = STEPS run in sequence; inner = tweeners run in parallel
   *  within their step. `NPowerFlashVfx.cs:97` opens a parallel block for the
   *  first pair and `:100` closes it, so the shape is `[2, 1, 1]`. */
  tween: SpriteShaderTweener[][];
  /** Sum of the steps' durations — 0.4 + 0.4 + 0.25. */
  lifetime: number;
}

export type VfxEffect =
  | FlipbookEffect
  | SpineEffect
  | ParticlesEffect
  | TransformAnimEffect
  | DissolveEffect
  | SpriteShaderEffect;

export interface VfxManifest {
  /** Bumped by the extractor. Frames are fetched with `?v=<rev>`. */
  rev: string;
  effects: Record<string, VfxEffect>;
  /** English UI text for the DOM-rendered (non-canvas) combat VFX panels —
   *  `extract_vfx.py`'s `VFX_LOC_STRINGS`, read out of the pck's
   *  `localization/eng/` rather than typed as a literal, per the same
   *  "loc rows, not literals" rule the rest of this manifest's art follows.
   *  Optional: an older or manifest-fetch-failed client has none, and
   *  `Combatant.tsx` falls back to its own (documented) English literal —
   *  see the fallback note beside each consumer. Known keys today:
   *  `blocked` ("Blocked", `vfx_blocked_text.tscn`) and `powerWearsOff`
   *  ("Wears Off", `power_removed_vfx.tscn`). */
  locStrings?: Record<string, string>;
}

/**
 * Fetch the VFX manifest.
 *
 * The art bucket serves assets `immutable` at non-content-addressed URLs —
 * the same trap that made the audio pipeline need `?v=audioRev`. The manifest
 * itself is busted with the app build id (its own `rev` is not known until it
 * has been fetched); the frames it names are then busted with that `rev` (see
 * `effectAssetUrl`).
 */
export async function loadManifest(fetchImpl: typeof fetch = fetch): Promise<VfxManifest> {
  const bust = import.meta.env?.VITE_BUILD_ID ?? String(Date.now());
  const r = await fetchImpl(`${assetUrl("vfx/manifest.json")}?v=${encodeURIComponent(bust)}`);
  if (!r.ok) throw new Error(`vfx manifest ${r.status}`);
  return (await r.json()) as VfxManifest;
}

/** URL for one manifest-named asset, cache-busted by the manifest's rev. */
export function effectAssetUrl(rel: string, rev: string): string {
  return `${assetUrl(rel)}?v=${encodeURIComponent(rev)}`;
}

// `effectsForEvent` moved to `bindings.ts` (Task 9) — it now selects from the
// extracted card/move -> VFX binding table (design spec §1.4) instead of the
// hardcoded slash/block guess this module used to make. Import it from
// `./bindings`, alongside `loadBindings` and the `Bindings` type.
