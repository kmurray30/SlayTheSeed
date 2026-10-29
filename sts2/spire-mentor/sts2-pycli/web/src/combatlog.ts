import type { CombatLogRow, LiveNode, LiveTimeline } from "./api";

/**
 * Row kinds that ride the wire for the VFX layer and must NOT be printed.
 *
 * `power_flash` is a VIEW signal, not a log line: the C#
 * `NCreature.OnPowerFlashed` that spawns `power_flash_vfx`. The game's own log
 * has no such row, and a busy fight produces dozens. It reaches the client
 * only because the board's effect stream needs it — `branching.py`'s
 * `_slice_display_logs` un-hides this one kind by name (`keep=`), so a
 * timeline node's `combat_log` carries it and nothing else it would not
 * otherwise have carried.
 *
 * So the split is deliberate: the VFX path (`activeNode` -> `actionStream`)
 * takes a node's rows whole, and the places that RENDER those rows to a reader
 * — `StepEvents` for both history ledgers (LiveLog / EventLog) — filter
 * through {@link readableRows} first, or the log fills with "Thorns
 * triggered." lines.
 *
 * One more consumer takes the rows UNFILTERED and is fine: `turnChips`
 * (`turnLedger.tsx`) is handed raw slices by both ledgers, but it sums only
 * `damage` and `block` by name and ignores every other kind, so a hidden row
 * contributes nothing. That safety is the whitelist's, not this filter's — a
 * future chip that aggregates by "whatever rows are here" would need
 * {@link readableRows} like the ledgers.
 *
 * This set is NOT a mirror of `combatlog.py`'s `_VERBOSE_ONLY` and must not
 * grow into one: the other six kinds that list hides never reach the client at
 * all, so re-listing them here would be dead weight that silently rots.
 */
export const PANEL_HIDDEN_KINDS: ReadonlySet<string> = new Set(["power_flash"]);

/** Whether `rows` holds anything a human should read — see {@link
 *  PANEL_HIDDEN_KINDS}. Equivalent to `readableRows(rows).length > 0` without
 *  building the filtered array, which both ledgers were doing per entry on
 *  every render just to test emptiness. */
export function hasReadableRow(rows: CombatLogRow[]): boolean {
  return rows.some((r) => !PANEL_HIDDEN_KINDS.has(r.kind));
}

/** The subset of `rows` a human should read — see {@link PANEL_HIDDEN_KINDS}. */
export function readableRows(rows: CombatLogRow[]): CombatLogRow[] {
  return rows.filter((r) => !PANEL_HIDDEN_KINDS.has(r.kind));
}

/**
 * The timeline node the run is currently standing on — the one whose action
 * produced the live state on screen.
 *
 * Its `combat_log` is EXACTLY that one action's per-hit rows, which is what
 * the board's effect stream needs — and it is returned UNFILTERED, so the
 * view-only `power_flash` rows {@link readableRows} drops still reach the
 * VFX layer. `CombatState.combat_log` is not: that field
 * is written only by `server.py`'s `_enrich`, from the `combat_log_raw` that
 * the real-game bridge recorders emit, so a workbench live step does not carry
 * it at all — and where it does exist it is the whole fight so far, which fed
 * to `actionStream` would replay every hit since turn 1 on a single card play.
 *
 * The node itself is returned rather than just its rows because the caller
 * also needs its identity: the active leaf advances exactly once per action,
 * and `parent_id` says whether that advance was a step FORWARD (an action) or
 * a jump (a seek).
 */
export function activeNode(timeline: LiveTimeline | null): LiveNode | null {
  if (!timeline) return null;
  const leafId = timeline.active_path[timeline.active_path.length - 1];
  if (leafId == null) return null;
  return timeline.nodes.find((n) => n.id === leafId) ?? null;
}
