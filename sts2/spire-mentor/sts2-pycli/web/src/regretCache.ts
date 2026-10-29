/**
 * Should opening the regret dock re-run the (expensive) server-side analysis?
 *
 * The dock caches the last `RegretReview` result so reopening it is instant.
 * We only pay for a fresh diff when there's nothing cached yet, or when the
 * cached result is for a different line than the one being requested
 * (live in-progress run vs. a loaded replay). The `↻ re-run` button bypasses
 * this and always re-runs.
 *
 * @param hasCache    whether a cached analysis currently exists
 * @param cachedLive  which line the cache is for (`null` when `hasCache` is false)
 * @param wantLive    the line being requested now
 */
export function shouldRerunRegret(
  hasCache: boolean,
  cachedLive: boolean | null,
  wantLive: boolean,
): boolean {
  return !hasCache || cachedLive !== wantLive;
}
