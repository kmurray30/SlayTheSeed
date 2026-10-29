import type { DecisionRegret, LiveTimeline } from "./api";

/**
 * Resolve a regret-review decision's `idx` to a live branch-tree node id.
 *
 * A `DecisionRegret.idx` is the decision's *depth from the root* (the position
 * the move was chosen from), not a replay-slider index — so in a live run the
 * replay `setIdx` is inert and clicking a blunder appeared to do nothing.
 * `review_run` builds each decision from a parent node on the active path
 * (`parent.idx`), so the seek target is the node on the active line whose `idx`
 * equals the decision depth.
 *
 * Returns the node id to pass to the live seek, or `null` when no such node is
 * on the active path (e.g. a stale review against a since-pruned line).
 *
 * @param timeline  the current live timeline (`null` when no run is live)
 * @param idx       the decision's depth-from-root (`DecisionRegret.idx`)
 */
export function liveSeekTargetForIdx(
  timeline: LiveTimeline | null,
  idx: number,
): number | null {
  if (!timeline) return null;
  const onPath = new Set(timeline.active_path);
  const node = timeline.nodes.find((n) => onPath.has(n.id) && n.idx === idx);
  return node ? node.id : null;
}

/**
 * Resolve a regret-review decision to the live branch-tree node to seek to.
 *
 * Prefers the decision's recorded `node_id` — the exact node the move was
 * chosen from — which is stable even after a prior seek has rewound (and thus
 * truncated) the active path, leaving later decisions on an off-path sibling
 * branch. Without it, mapping by depth alone (`liveSeekTargetForIdx`) silently
 * returns `null` for any decision deeper than the current position, so clicking
 * it does nothing (the "decisions stop jumping after you rewind once" bug).
 *
 * Falls back to depth-on-active-path resolution for older payloads that predate
 * `node_id`. The backend `seek` replays from the root via parent pointers, so a
 * target on a pruned branch still rewinds correctly.
 *
 * @param timeline  the current live timeline (`null` when no run is live)
 * @param d         the regret-review decision that was clicked
 */
export function liveSeekTargetForDecision(
  timeline: LiveTimeline | null,
  d: Pick<DecisionRegret, "idx" | "node_id">,
): number | null {
  if (d.node_id != null) return d.node_id;
  return liveSeekTargetForIdx(timeline, d.idx);
}
