import { useCallback, useEffect, useRef, useState } from "react";
import {
  canDownloadForOffline,
  downloadForOffline,
  formatMb,
  isOfflineReady,
  offlineStatus,
  type OfflineProgress,
  type OfflineStatus,
} from "../offline";

/** Topbar button of the browser-only build: fill the art + PPO caches so
 *  every fight renders with the network off. Idle → running (progress) →
 *  ready; a partial download resumes where it stopped (cached files are
 *  skipped), so a closed tab costs nothing. Hidden where the page cannot
 *  write caches (non-secure context) or the host emits no art list. */
export default function OfflineButton() {
  const [status, setStatus] = useState<OfflineStatus | null>(null);
  const [progress, setProgress] = useState<OfflineProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const abort = useRef<AbortController | null>(null);

  const refresh = useCallback(() => {
    if (!canDownloadForOffline()) return;
    offlineStatus()
      .then(setStatus)
      .catch(() => setStatus(null));
  }, []);
  useEffect(() => {
    refresh();
    return () => abort.current?.abort();
  }, [refresh]);

  if (!status || status.targets.art.length === 0) return null;
  const ready = isOfflineReady(status);
  const total = status.targets.art.length + status.targets.ppo.length;
  const cached = status.cachedArt + status.cachedPpo;

  async function start() {
    setError(null);
    const ctrl = new AbortController();
    abort.current = ctrl;
    setProgress({ done: 0, total, failed: 0, bytes: 0 });
    try {
      const final = await downloadForOffline(status!.targets, { signal: ctrl.signal, onProgress: setProgress });
      if (final.failed > 0) setError(`${final.failed} files could not be fetched — try again later`);
    } catch (e) {
      if (!ctrl.signal.aborted) setError(String(e));
    } finally {
      abort.current = null;
      setProgress(null);
      refresh();
    }
  }
  function stop() {
    abort.current?.abort();
  }

  // One <button> in every state, so React keeps the same DOM node when the
  // state flips (idle → running → ready). The control is hosted as a row of
  // the Tools menu, which closes when focus leaves it: a remounted button
  // would drop focus and snap the menu shut on the click that started the
  // download.
  //
  // The partial-progress count is a separate muted piece, not part of the
  // idle label: as one label ("⬇ Download for offline (330/4156 cached)") it
  // was the widest thing in the menu and ran past the menu's edge.
  if (progress) {
    const pct = progress.total ? Math.floor((100 * progress.done) / progress.total) : 0;
    return (
      <>
        <button onClick={stop} className="mono" aria-live="polite" data-testid="offline-progress"
          title="downloading the art and the PPO nets into this device's cache; click to pause (resumes where it stopped)">
          ⬇ {pct}% · {progress.done}/{progress.total} · {formatMb(progress.bytes)}
        </button>
      </>
    );
  }
  if (ready) {
    return (
      <>
        <button disabled className="mono" data-testid="offline-ready"
          title={`every art file and PPO net is cached on this device (${formatMb(status.targets.artBytes)} of art) — the app works with the network off`}>
          ✓ Offline ready
        </button>
      </>
    );
  }
  return (
    <>
      <button onClick={start} className="mono" data-testid="offline-download"
        title={`fetch every art file and the five PPO nets into this device's cache (~${formatMb(status.targets.artBytes)} of art + ~42 MB of nets, once), so any fight renders offline${error ? ` — last attempt: ${error}` : ""}`}
        style={error ? { color: "var(--hp)" } : undefined}>
        ⬇ Download for offline
      </button>
      {cached > 0 && (
        <span className="mono" data-testid="offline-partial"
          style={{ color: "var(--muted)", fontSize: 11, whiteSpace: "nowrap" }}
          title="already in this device's cache; the download resumes from here">
          {cached}/{total} cached
        </span>
      )}
    </>
  );
}
