/**
 * The dying-creature DISSOLVE — a port of `shaders/dissolve.gdshader`
 * (v0.107.1 pck) plus the FBO plumbing that lets a live `SpineSprite` feed
 * it, replacing the old CSS grayscale fade (`.gs-dying`) with the real
 * shader (Task 14).
 *
 * `vfx_monster_death` (Task 10's `DissolveEffect`, `effects/manifest.ts`)
 * snapshots the dying creature into a `SubViewport` and eats it away with
 * this shader. The debris `CPUParticles2D` burst that same scene also
 * carries (`DissolveEffect.emitters`) is not drawn by anything HERE — this
 * module and `SpineSprite`'s consumption of it are the dissolve SPRITE only.
 * The burst is a separate consumer and now exists: it rides the shared
 * `VfxLayer` overlay as an ordinary particle composite
 * (`buildLiveEffect`'s `dissolve` branch), which is what gives it the
 * default alpha-mix blend rather than this sprite's `blend_premul_alpha` —
 * see `DissolveEffect.blendMode`'s own doc. What it needs from this module
 * is `deathBurstRadiusFor` below, the per-creature emission radius.
 *
 * Every WebGL helper below is created and owned PER `SpineSprite` INSTANCE,
 * not shared the way `effects/particles/shaders.ts`'s programs are shared
 * across `VfxLayer`'s one overlay canvas: each dying creature's `SpineSprite`
 * runs its own `spine.SpineCanvas`, i.e. its own independent
 * `WebGLRenderingContext`, and a `WebGLProgram`/`WebGLTexture`/
 * `WebGLFramebuffer` handle is meaningless outside the context that created
 * it. Only the pure `dissolveThreshold` math and the plain-DOM `loadImage`
 * fetch are context-free and worth sharing at all.
 */

// ---------------------------------------------------------------------------
// The threshold tween: `NMonsterDeathVfx.cs:239`'s
// `TweenProperty(material, "shader_parameter/threshold", 0f, num2)`
// `.SetEase((EaseType)1).SetTrans((TransitionType)1)` == EASE_OUT/TRANS_SINE.
// ---------------------------------------------------------------------------

/**
 * The shader's `threshold` uniform at `elapsed` seconds into a dissolve
 * whose ceiling is `duration` seconds (`DissolveEffect.dissolveSeconds`).
 *
 * Runs 1 -> 0 — fully VISIBLE to fully DISSOLVED — not 0 -> 1. The tween's
 * own target is `0f` (`NMonsterDeathVfx.cs:239`; `DissolveEffect.thresholdFrom`
 * is 1, `.thresholdTo` is 0), and the shader's own
 * `step(combinedDissolveStrength, threshold)` returns 1 (opaque) wherever
 * `combined <= threshold` — so `threshold = 1` passes nearly every pixel
 * (alive) and `threshold = 0` passes nearly none (gone). The desaturation
 * line agrees: `mix(grey, rgb, threshold)` is full colour at 1, grey at 0 —
 * the corpse drains of colour as it dissolves, not into it.
 *
 * Eased EASE_OUT/TRANS_SINE (Godot `Tween` enums 1/1 — confirmed directly
 * against Godot's own class reference,
 * `docs.godotengine.org/en/stable/classes/class_tween.html`: `EaseType`
 * `EASE_IN=0, EASE_OUT=1, EASE_IN_OUT=2, EASE_OUT_IN=3`; `TransitionType`
 * `TRANS_LINEAR=0, TRANS_SINE=1, TRANS_QUINT=2, TRANS_QUART=3, TRANS_QUAD=4,
 * TRANS_EXPO=5, TRANS_ELASTIC=6, TRANS_CUBIC=7, ...`), Godot's own
 * Penner-style sine curve — `sin(t * PI/2)` for ease-out — the SAME formula
 * `components/VfxLayer.tsx`'s `evaluateEase` already uses for
 * `vfx_power_flash_vfx`'s TRANS_SINE/EASE_OUT tweeners. NOT linear: at the
 * halfway point the eased fraction is `sin(PI/4) ≈ 0.7071`, so
 * `threshold ≈ 1 - 0.7071 ≈ 0.2929`, not 0.5.
 *
 * Clamped past the end (`elapsed >= duration`) to the tween's own END value,
 * 0 — NOT 1: a corpse whose removal is late must stay fully dissolved, never
 * snap back to visible.
 */
export function dissolveThreshold(elapsed: number, duration: number): number {
  const t = duration > 0 ? Math.min(Math.max(elapsed / duration, 0), 1) : 1;
  const eased = Math.sin((t * Math.PI) / 2); // EASE_OUT / TRANS_SINE
  return 1 - eased; // thresholdFrom(1) + (thresholdTo(0) - thresholdFrom(1)) * eased
}

/**
 * `MonsterModel.defaultDeathVfxPadding` (`MonsterModel.cs:173`): the bounds are
 * grown by 1.2 in both axes before the viewport is sized.
 *
 * The two monsters that override it — `CalcifiedCultist` and `DampCultist`,
 * both `(1.5, 1.2)` — are provably inert HERE, whatever they do to the
 * viewport's shape. The duration depends on the padded box only through
 * `max(w, h)`, and the shared `cultists` rig is 870x1291 rig units: `h` wins
 * under either x padding (1291*1.2 = 1549 beats both 870*1.2 and 870*1.5), and
 * the y padding is 1.2 in both. So nothing on the wire needs to carry a
 * per-monster padding for this, and a monster whose width DID dominate would.
 */
export const DEATH_VFX_PADDING = 1.2;

/**
 * How long one creature's dissolve runs, in seconds — `NMonsterDeathVfx.cs`,
 * whose duration is per-monster and not the flat cap the manifest ships.
 *
 * ```text
 * :202-204  int num = Mathf.RoundToInt(Mathf.Max(size.X, size.Y));
 *           node.Size = new Vector2I(num, num) * 2;
 * :233-235  float num  = 0.1f * GetViewportRect().Size.X;
 *           float num2 = Math.Min((float)node.Size.X / num * 2.5f, 2.5f);
 * ```
 *
 * So the ratio is (twice the creature's larger padded extent) over a tenth of
 * the screen width, and the `Math.Min` binds — the full 2.5 s is reached — once
 * that larger extent is about 5% of the screen. A Byrdpip goes faster than a
 * Fabricator, which is the whole point: a flat cap made small monsters linger.
 *
 * Both terms are taken in SCENE px against `SCENE_W`, not in stage px against
 * the browser window. That is not an approximation of the game's real viewport
 * — it is the same number. The C# multiplies the bounds by
 * `SceneContainer.Scale` and divides by the true window width, and Godot's
 * stretch scales the whole canvas together, so the ratio is invariant and its
 * value is the one taken at the 1920-wide design size.
 *
 * `boundsW`/`boundsH` are the creature's DRAWN box in scene px at its authored
 * scale (`scene.ts`'s `renderedSize`, times any per-instance size jitter) —
 * the game's `skeleton.GetBounds() * body.Scale`. `cameraScale` is the
 * encounter camera, the C#'s `SceneContainer.Scale`.
 *
 * Returns `maxSeconds` unchanged for a creature with no measurable box, which
 * is the pre-existing behaviour and the safe direction: a dissolve that runs
 * long is finished early by the corpse's removal, one that runs short is cut
 * off mid-fade.
 */
export function dissolveSecondsFor(
  boundsW: number,
  boundsH: number,
  cameraScale: number,
  maxSeconds: number,
  sceneWidth: number,
): number {
  const num = deathVfxExtent(boundsW, boundsH, cameraScale);
  if (num === null || !(sceneWidth > 0)) return maxSeconds;
  const viewportSize = 2 * num; // `node.Size = Vector2I(num, num) * 2`
  return Math.min((viewportSize / (0.1 * sceneWidth)) * maxSeconds, maxSeconds);
}

/**
 * `NMonsterDeathVfx.cs:202`'s `num` — the creature's larger padded extent in
 * scene px, rounded to a whole pixel the way the C# does before it sizes the
 * `SubViewport`:
 *
 * ```text
 * :202  int num = Mathf.RoundToInt(Mathf.Max(size.X, size.Y));
 * ```
 *
 * Shared by the two things that read it (`dissolveSecondsFor` and
 * `deathBurstRadiusFor`) so the rounding cannot drift between them: the
 * duration and the burst's radius are two divisions of the SAME integer, and
 * the game derives both from this one line.
 *
 * `null` for a creature with no measurable box — each caller has its own safe
 * answer for that and neither should invent an extent.
 */
export function deathVfxExtent(
  boundsW: number,
  boundsH: number,
  cameraScale: number,
): number | null {
  const larger = Math.max(boundsW, boundsH) * DEATH_VFX_PADDING * cameraScale;
  return larger > 0 ? Math.round(larger) : null;
}

/**
 * The debris burst's `emission_sphere_radius`, in scene px — the number the
 * game computes per creature and the scene cannot carry.
 *
 * ```text
 * :202-204  int num = Mathf.RoundToInt(Mathf.Max(size.X, size.Y));
 *           node.Size = new Vector2I(num, num) * 2;
 * :231      node3.EmissionSphereRadius = (float)node.Size.Y / 4f;
 * ```
 *
 * `(2 * num) / 4` is `num / 2`, i.e. `perExtent` (the manifest's
 * `emissionRadiusPerCreatureExtent`, 0.5) times the creature's larger padded
 * extent. `extract_vfx.py` blanks the scene's authored 99.23 to 0 precisely
 * so this has to be supplied — a burst that reaches the screen at the
 * scene's placeholder would be the same size for a Byrdpip and a Fabricator.
 *
 * `null` for an unmeasurable creature: the honest answer is "don't play the
 * burst", not "play it at some default size".
 */
export function deathBurstRadiusFor(
  boundsW: number,
  boundsH: number,
  cameraScale: number,
  perExtent: number,
): number | null {
  const num = deathVfxExtent(boundsW, boundsH, cameraScale);
  return num === null ? null : perExtent * num;
}

// ---------------------------------------------------------------------------
// Asset loading: the two baked 512x512 gradient PNGs
// (`DissolveEffect.gradient1`/`.gradient2`). Context-free (a plain DOM
// `Image` fetch) — the GL upload itself is per-instance (see
// `createGradientTexture` below).
// ---------------------------------------------------------------------------

/** Loads one image via the browser's `Image` element. `crossOrigin` is set
 *  because the art bucket may serve this from a different origin than the
 *  app itself, and an un-flagged cross-origin image taints any canvas that
 *  later samples it via `gl.texImage2D`. */
export function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`dissolve gradient image failed to load: ${url}`));
    img.src = url;
  });
}

// ---------------------------------------------------------------------------
// WebGL: the dissolve post-pass shader program.
// ---------------------------------------------------------------------------

export const DISSOLVE_VERTEX_SHADER = `
attribute vec2 a_position;
attribute vec2 a_uv;
varying vec2 v_uv;
void main() {
  v_uv = a_uv;
  gl_Position = vec4(a_position, 0.0, 1.0);
}
`;

/**
 * Transcribed from `shaders/dissolve.gdshader` (v0.107.1 pck):
 *
 * ```text
 * shader_type canvas_item;
 * render_mode blend_premul_alpha;
 *
 * uniform sampler2D dissolveGradient1 : repeat_enable;
 * uniform sampler2D dissolveGradient2 : repeat_enable;
 * uniform float threshold : hint_range(0.0, 1.0);
 *
 * float get_dissolve_strength(vec2 uv, float offset, vec2 texSize) {
 *     const float refSize = 1.0 / 400.0;
 *     vec2 scaling = refSize / texSize;
 *     uv.x += offset;
 *     float yOffset = pow(uv.x, 3);
 *     float dissolveStrength1 = (texture(dissolveGradient1, uv * scaling + vec2(-TIME * 0.25, yOffset)).r + uv.x) * 0.5;
 *     float dissolveStrength2 = (texture(dissolveGradient2, uv * scaling + vec2(-TIME * 0.25, yOffset)).r + uv.x) * 0.5;
 *     float combinedDissolveStrength = mix(dissolveStrength1, dissolveStrength2, 0.4);
 *     return step(combinedDissolveStrength, threshold);
 * }
 *
 * void fragment() {
 *     const float upWarpAmount = 1024.0;
 *     const float leftWarpAmount = 200.0;
 *     vec2 warpAmount = vec2(-leftWarpAmount, upWarpAmount) * TEXTURE_PIXEL_SIZE;
 *     vec2 offset = max(UV.x - (threshold * 1.9 - 0.55), 0.0) * warpAmount;
 *     vec4 texCol = texture(TEXTURE, UV + offset * offset);
 *
 *     float desatMask = get_dissolve_strength(UV, 0.05, TEXTURE_PIXEL_SIZE);
 *     texCol.rgb = mix(vec3(0,0,0), texCol.rgb, desatMask);
 *     texCol.rgb = mix(vec3(dot(texCol.rgb, vec3(0.299, 0.587, 0.114))), texCol.rgb, threshold);
 *     COLOR = texCol * get_dissolve_strength(UV, 0, TEXTURE_PIXEL_SIZE);
 * }
 * ```
 *
 * Godot builtins become explicit uniforms/varyings, since this is a plain
 * WebGL1 program, not a canvas_item shader:
 *
 *  - `TEXTURE`/`UV` -> `u_tex`/`v_uv`: the FBO the dying rig was captured
 *    into (`createRigFramebuffer` below), sampled full-canvas.
 *  - `TIME` -> `u_time`: seconds since THIS dissolve instance's own tween
 *    started (`NMonsterDeathVfx.PlayVfxInternal`'s tween clock — the brief's
 *    own reading of Godot's `TIME`, "seconds since the effect started" — not
 *    wall-clock since page load, which would desync every simultaneously-
 *    dissolving corpse's noise scroll from a freshly-mounted one).
 *  - `TEXTURE_PIXEL_SIZE` -> `u_texelSize`: `1 / [FBO width, FBO height]` —
 *    a RECIPROCAL. `get_dissolve_strength`'s `scaling = refSize / texSize`
 *    GROWS with resolution because of this; passing raw pixel dimensions
 *    here instead would invert that scale (the transcription trap the Task
 *    14 addendum calls out by name).
 *
 * `get_dissolve_strength` is called twice per fragment (desaturation mask at
 * `offset = 0.05`, the final cutout at `offset = 0`) and is ported as a GLSL
 * function taking `uv` BY VALUE — GLSL (like the source) copies a `vec2`
 * argument, so `uv.x += offset` inside it never leaks back to `v_uv`.
 *
 * `COLOR = texCol * get_dissolve_strength(...)` multiplies ALL FOUR channels
 * — including alpha — by the (0 or 1) mask, which is what makes
 * `blend_premul_alpha`'s `(ONE, ONE_MINUS_SRC_ALPHA)` blend correct for a
 * fully-cut pixel (mask 0 contributes nothing), even though the sampled
 * `TEXTURE` is itself STRAIGHT, not premultiplied, alpha (`SpineSprite`
 * always draws with `premultipliedAlpha: false` / `drawSkeleton(skeleton,
 * false)`). Transcribed verbatim, not "fixed" — see the brief's own note on
 * this blend mode, and `particles/shaders.ts`'s `drawPowerFlashQuad` doc for
 * why this is a DIFFERENT blend function from that shader's `blend_add`.
 *
 * `u_modulateAlpha` is `DissolveEffect.modulateAlpha` — the scene's `Visual`
 * sprite `modulate` (0.4667), which that field's own doc establishes applies
 * to the dissolve sprite itself (as well as the not-yet-drawn debris) —
 * folded in as a straight multiply on the final output, the same way the
 * mask above already scales all four channels.
 */
export const DISSOLVE_FRAGMENT_SHADER = `
precision mediump float;
uniform sampler2D u_tex;
uniform sampler2D u_gradient1;
uniform sampler2D u_gradient2;
uniform float u_threshold;
uniform float u_time;
uniform vec2 u_texelSize;
uniform float u_modulateAlpha;
varying vec2 v_uv;

float get_dissolve_strength(vec2 uv, float offset) {
  const float refSize = 1.0 / 400.0;
  vec2 scaling = refSize / u_texelSize;
  uv.x += offset;
  float yOffset = pow(uv.x, 3.0);
  vec2 baseUv = uv * scaling + vec2(-u_time * 0.25, yOffset);
  float d1 = (texture2D(u_gradient1, baseUv).r + uv.x) * 0.5;
  float d2 = (texture2D(u_gradient2, baseUv).r + uv.x) * 0.5;
  float combined = mix(d1, d2, 0.4);
  return step(combined, u_threshold);
}

void main() {
  const float upWarpAmount = 1024.0;
  const float leftWarpAmount = 200.0;
  vec2 warpAmount = vec2(-leftWarpAmount, upWarpAmount) * u_texelSize;
  vec2 offset = max(v_uv.x - (u_threshold * 1.9 - 0.55), 0.0) * warpAmount;
  vec4 texCol = texture2D(u_tex, v_uv + offset * offset);

  float desatMask = get_dissolve_strength(v_uv, 0.05);
  texCol.rgb = mix(vec3(0.0), texCol.rgb, desatMask);
  texCol.rgb = mix(vec3(dot(texCol.rgb, vec3(0.299, 0.587, 0.114))), texCol.rgb, u_threshold);
  gl_FragColor = texCol * get_dissolve_strength(v_uv, 0.0) * u_modulateAlpha;
}
`;

export interface DissolveProgram {
  program: WebGLProgram;
  aPosition: number;
  aUv: number;
  uTex: WebGLUniformLocation | null;
  uGradient1: WebGLUniformLocation | null;
  uGradient2: WebGLUniformLocation | null;
  uThreshold: WebGLUniformLocation | null;
  uTime: WebGLUniformLocation | null;
  uTexelSize: WebGLUniformLocation | null;
  uModulateAlpha: WebGLUniformLocation | null;
  buffer: WebGLBuffer;
}

function compileShader(gl: WebGLRenderingContext, type: number, src: string): WebGLShader {
  const shader = gl.createShader(type);
  if (!shader) throw new Error("dissolve shader: createShader failed");
  gl.shaderSource(shader, src);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const info = gl.getShaderInfoLog(shader);
    gl.deleteShader(shader);
    throw new Error(`dissolve shader compile failed: ${info ?? "unknown error"}`);
  }
  return shader;
}

/** Compiles + links the dissolve program. One per `SpineSprite` instance —
 *  see the module doc for why this can't be shared the way `VfxLayer`'s
 *  particle/power-flash programs are shared across its one canvas. */
export function createDissolveProgram(gl: WebGLRenderingContext): DissolveProgram {
  const vs = compileShader(gl, gl.VERTEX_SHADER, DISSOLVE_VERTEX_SHADER);
  const fs = compileShader(gl, gl.FRAGMENT_SHADER, DISSOLVE_FRAGMENT_SHADER);
  const program = gl.createProgram();
  if (!program) throw new Error("dissolve program: createProgram failed");
  gl.attachShader(program, vs);
  gl.attachShader(program, fs);
  gl.linkProgram(program);
  gl.deleteShader(vs);
  gl.deleteShader(fs);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const info = gl.getProgramInfoLog(program);
    gl.deleteProgram(program);
    throw new Error(`dissolve program link failed: ${info ?? "unknown error"}`);
  }
  const buffer = gl.createBuffer();
  if (!buffer) throw new Error("dissolve program: createBuffer failed");
  return {
    program,
    aPosition: gl.getAttribLocation(program, "a_position"),
    aUv: gl.getAttribLocation(program, "a_uv"),
    uTex: gl.getUniformLocation(program, "u_tex"),
    uGradient1: gl.getUniformLocation(program, "u_gradient1"),
    uGradient2: gl.getUniformLocation(program, "u_gradient2"),
    uThreshold: gl.getUniformLocation(program, "u_threshold"),
    uTime: gl.getUniformLocation(program, "u_time"),
    uTexelSize: gl.getUniformLocation(program, "u_texelSize"),
    uModulateAlpha: gl.getUniformLocation(program, "u_modulateAlpha"),
    buffer,
  };
}

export interface RigFramebuffer {
  framebuffer: WebGLFramebuffer;
  texture: WebGLTexture;
  width: number;
  height: number;
}

/** Allocates an FBO + backing texture sized to the drawing buffer, for
 *  `SpineSprite`'s dissolve post-pass to render the live rig into: bind this,
 *  run the ordinary `r.begin()/drawSkeleton/r.end()` into it, unbind, then
 *  draw its texture full-canvas through the dissolve program. RGBA/
 *  UNSIGNED_BYTE, CLAMP wrap (sampled once per fragment at `v_uv` — no
 *  repeat needed, unlike the two baked gradients) and NEAREST filtering
 *  (sampled 1:1 against the canvas it was captured from, so there is nothing
 *  to resample).
 *
 *  Callers own recreating this when the size changes (a corpse can be
 *  mid-dissolve across an arena re-fit) — nothing here disposes the
 *  previous one, so the caller must `gl.deleteFramebuffer`/`gl.deleteTexture`
 *  the old one itself before replacing it (`SpineSprite.tsx`'s `render()`
 *  does, for both this FBO and the gradient texture pair `createGradientTexture`
 *  returns). This is DISTINCT from the "no explicit disposal at unmount"
 *  convention the rest of this module's per-instance resources follow (see
 *  the module doc): that one fires once per rig load and is bounded by GC
 *  once the canvas is discarded; a resize/gradient-key leak here can recur
 *  repeatedly within a single ~2.5s dissolve (every arena re-fit while a
 *  monster is dying) and would accumulate without bound if the caller ever
 *  stopped deleting the superseded resource. */
export function createRigFramebuffer(gl: WebGLRenderingContext, w: number, h: number): RigFramebuffer {
  const texture = gl.createTexture();
  if (!texture) throw new Error("dissolve fbo: createTexture failed");
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);

  const framebuffer = gl.createFramebuffer();
  if (!framebuffer) {
    gl.deleteTexture(texture);
    throw new Error("dissolve fbo: createFramebuffer failed");
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  gl.bindTexture(gl.TEXTURE_2D, null);
  return { framebuffer, texture, width: w, height: h };
}

/** Uploads one baked 512x512 grayscale gradient PNG (`DissolveEffect.gradient1`
 *  /`.gradient2`) as a `gl.REPEAT`-wrapped texture — both are declared
 *  `: repeat_enable` in the source shader, and 512 is a power of two, which
 *  WebGL1 requires for that wrap mode. No `UNPACK_FLIP_Y_WEBGL`, matching
 *  every other baked PNG this app loads (`particles/shaders.ts`'s
 *  `appendQuadVertices` doc: "nothing here sets UNPACK_FLIP_Y_WEBGL — Spine's
 *  GLTexture does not, and neither does any app code") — moot for this
 *  texture anyway, since it is sampled only through a scrolling, tiling
 *  formula with no "which way is up" meaning of its own.
 *
 *  Callers own disposing the PAIR this returns when the source paths
 *  change (`DissolveEffect.gradient1`/`.gradient2` are effectively
 *  constant today, but nothing here assumes that) — same "caller deletes
 *  the superseded resource" contract as `createRigFramebuffer`'s doc. */
export function createGradientTexture(gl: WebGLRenderingContext, image: TexImageSource): WebGLTexture {
  const texture = gl.createTexture();
  if (!texture) throw new Error("dissolve gradient: createTexture failed");
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  return texture;
}

/**
 * Draws the dissolve post-pass: the FBO-captured rig, full-canvas, through
 * the dissolve program, onto whatever framebuffer is currently bound
 * (`SpineSprite` unbinds the FBO and clears the default framebuffer before
 * calling this).
 *
 * `render_mode blend_premul_alpha` -> `gl.blendFunc(gl.ONE,
 * gl.ONE_MINUS_SRC_ALPHA)` — NOT the particle family's straight-alpha mix
 * (`gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA`), and NOT power-flash's
 * `blend_add` (`gl.SRC_ALPHA, gl.ONE`) — see `particles/shaders.ts`'s
 * `drawPowerFlashQuad` doc for that three-way distinction.
 */
export function drawDissolveQuad(
  gl: WebGLRenderingContext,
  prog: DissolveProgram,
  rigTexture: WebGLTexture,
  gradient1: WebGLTexture,
  gradient2: WebGLTexture,
  params: { threshold: number; time: number; texelSize: readonly [number, number]; modulateAlpha: number },
): void {
  gl.useProgram(prog.program);

  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, rigTexture);
  gl.uniform1i(prog.uTex, 0);
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_2D, gradient1);
  gl.uniform1i(prog.uGradient1, 1);
  gl.activeTexture(gl.TEXTURE2);
  gl.bindTexture(gl.TEXTURE_2D, gradient2);
  gl.uniform1i(prog.uGradient2, 2);
  gl.activeTexture(gl.TEXTURE0);

  gl.uniform1f(prog.uThreshold, params.threshold);
  gl.uniform1f(prog.uTime, params.time);
  gl.uniform2f(prog.uTexelSize, params.texelSize[0], params.texelSize[1]);
  gl.uniform1f(prog.uModulateAlpha, params.modulateAlpha);

  // Full-canvas quad, clip space [-1, 1]. UV matches how the FBO texture was
  // written: the FBO is rendered with the same bottom-left-origin convention
  // as the default framebuffer (no image ever passes through it — it is
  // written and read entirely on the GPU side), so a straight, unflipped UV
  // (v=0 at the bottom edge) samples it right-side up.
  const verts = new Float32Array([
    -1, -1, 0, 0,
    1, -1, 1, 0,
    1, 1, 1, 1,
    -1, -1, 0, 0,
    1, 1, 1, 1,
    -1, 1, 0, 1,
  ]);
  gl.bindBuffer(gl.ARRAY_BUFFER, prog.buffer);
  gl.bufferData(gl.ARRAY_BUFFER, verts, gl.DYNAMIC_DRAW);
  const stride = 4 * 4;
  gl.enableVertexAttribArray(prog.aPosition);
  gl.vertexAttribPointer(prog.aPosition, 2, gl.FLOAT, false, stride, 0);
  gl.enableVertexAttribArray(prog.aUv);
  gl.vertexAttribPointer(prog.aUv, 2, gl.FLOAT, false, stride, 8);

  gl.enable(gl.BLEND);
  gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
}
