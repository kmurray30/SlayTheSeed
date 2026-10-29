/** The five playable characters, keyed the way the art tree names them. */
export const CHARACTER_KEYS: ReadonlySet<string> = new Set([
  "ironclad", "silent", "defect", "regent", "necrobinder",
]);

/** ``state.player.name`` → the art-tree key, or ``null`` when we ship no art.
 *
 *  Mirrors `workbench/assets.py slugify`, **including its leading-"the"
 *  strip**. The two backends disagree on the shape of this field: the engine
 *  lane ships the bare `"Ironclad"`, while the live-game lane ships the
 *  localized title — `sts2_realgame._character_title` builds `"The " +
 *  <Title Case>` from `characters.json`'s `<ID>.title`, which every one of the
 *  five carries. Both have to land on the same key.
 *
 *  This lives in one place because it did not: three components each derived
 *  it separately and none of them stripped the article, so every live-game run
 *  silently lost the rest-site hero, the run-end sprite and the HUD portrait
 *  while combat still looked right (that path uses the server-resolved
 *  `player.rig`). */
export function characterKey(name?: string | null): string | null {
  const k = (name ?? "")
    .trim()
    .toLowerCase()
    .replace(/^the\s+/, "")
    .replace(/[^a-z]/g, "");
  return CHARACTER_KEYS.has(k) ? k : null;
}
