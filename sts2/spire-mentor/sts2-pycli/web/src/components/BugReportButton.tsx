import { useState } from "react";
import { api } from "../api";
import type { BugReportResult } from "./BugReportToast";

/** Freeze the current workbench moment to a bug-report dump. `mode` is the
 *  SPA's view mode ("replay" | "play" | "mcts"); it is translated to the API's
 *  "live"/"replay".
 *
 *  The id/URL is handed UP rather than shown here: this button is a row in the
 *  Tools dropdown, and the dropdown unmounts its rows as soon as it closes —
 *  which the note prompt alone is usually enough to do — so an inline result
 *  was destroyed before it could be read (user report 2026-09-12).
 *  `BugReportToast`, rendered by `App`, outlives the menu. */
export default function BugReportButton({
  mode,
  step,
  screen,
  runPath,
  onCaptured,
  onFailed,
}: {
  mode: string;
  step: number | null;
  screen: string | null;
  runPath: string | null;
  onCaptured: (result: BugReportResult) => void;
  onFailed: (message: string) => void;
}) {
  const [busy, setBusy] = useState(false);

  async function capture() {
    setBusy(true);
    const note = window.prompt("Describe the bug (optional):") ?? null;
    const apiMode = mode === "replay" ? "replay" : "live";
    try {
      const res = await api.captureBugReport({
        mode: apiMode,
        step: apiMode === "replay" ? step : null,
        screen,
        note,
        run_path: apiMode === "replay" ? runPath : null,
      });
      onCaptured(res);
    } catch (e) {
      onFailed(e instanceof Error ? e.message : "capture failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="bugreport">
      <button type="button" onClick={capture} disabled={busy} title="Capture a bug report">
        🐞 Report
      </button>
    </span>
  );
}
