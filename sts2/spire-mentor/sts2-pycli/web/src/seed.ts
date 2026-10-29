/** Run seeds, the way the game makes and reads them.
 *
 *  Mirrors `MegaCrit.Sts2.Core.Helpers.SeedHelper` (v0.107.1 decomp): a seed
 *  is 10 characters drawn from `0-9 A-Z` with `I` and `O` left out — the
 *  glyphs a player would confuse with `1` and `0` — and any typed seed is
 *  canonicalized (trim, uppercase, `O`→`0`, `I`→`1`) before it is hashed.
 *  Both engines hash the CANONICAL string (`workbench/seed.py`), so the
 *  launcher shows the seed the way it will actually be used. */

export const SEED_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
export const SEED_LENGTH = 10;

/** A fresh game-style seed. `rng` is injectable for tests; the default is
 *  `Math.random`, which is the equivalent of the game's `Rng.Chaotic`. */
export function generateSeed(rng: () => number = Math.random, length = SEED_LENGTH): string {
  let s = "";
  for (let i = 0; i < length; i++) {
    const k = Math.min(SEED_ALPHABET.length - 1, Math.floor(rng() * SEED_ALPHABET.length));
    s += SEED_ALPHABET[k];
  }
  return s;
}

/** `SeedHelper.CanonicalizeSeed`: what the server will hash for a typed seed. */
export function canonicalizeSeed(seed: string): string {
  return seed.trim().toUpperCase().replace(/O/g, "0").replace(/I/g, "1");
}
