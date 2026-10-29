import type { Step } from "./api";

/** A run that was just ended (abandoned mid-run, or closed after game over),
 *  captured for the summary screen. `logPath` is the recorded run file when the
 *  backend returned one (drives "Review replay"); null when quit failed or the
 *  run left no log. */
export interface EndedRun {
  step: Step;
  logPath: string | null;
}

/** Compute the result of ending the live run WITHOUT ever rejecting.
 *
 * `quit` is the server call (``api.live.quit``). It can reject — e.g. HTTP 400
 * "no live run" when the run already reached game over, or any backend error.
 * We swallow that: the UI must always tear down and show the summary so a live
 * run can never be stranded on screen (the "End run does nothing" bug). A
 * successful quit's `log_path` is carried through for the replay review.
 */
export async function computeRunEnd(
  snapshot: Step,
  quit: () => Promise<{ log_path: string | null }>,
): Promise<EndedRun> {
  let logPath: string | null = null;
  try {
    logPath = (await quit()).log_path ?? null;
  } catch {
    logPath = null;
  }
  return { step: snapshot, logPath };
}

/** Whether the Live/Replay view switch has anything to switch between. Hidden on
 *  a fresh workbench (no live run, no just-ended run, no loaded replay) so the
 *  user isn't offered a toggle onto a bare "no run loaded" screen. */
export function shouldShowViewToggle(opts: {
  hasLive: boolean;
  hasEndedRun: boolean;
  replayCount: number;
}): boolean {
  return opts.hasLive || opts.hasEndedRun || opts.replayCount > 0;
}
