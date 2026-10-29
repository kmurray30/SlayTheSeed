/** Service-worker registration for the browser-only (static) build.
 *
 *  Only the static build is a PWA: it has no server, so the whole app (the
 *  hashed chunks, the wasm engine) can be cached and reopened offline, and
 *  the art is cached as it is seen. The dev / Cloud Run builds keep talking
 *  to a backend and must not be cached by a worker that has never heard of
 *  it — so this is a no-op unless `VITE_WORKBENCH_MODE=static`.
 */
import { configuredAssetsBase, isStaticMode } from "./transport";

export function registerWorkbenchServiceWorker(): void {
  if (!isStaticMode()) return;
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;
  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("/sw.js")
      .then((reg) => {
        const tell = (sw: ServiceWorker | null) =>
          sw?.postMessage({ type: "assets-base", base: configuredAssetsBase() });
        tell(reg.active);
        reg.addEventListener("updatefound", () => tell(reg.installing));
        // A durable run needs durable storage: IndexedDB holds the branch
        // tree and the saves, and the art cache is large. Chrome grants this
        // silently to installed apps and to sites the user engages with.
        navigator.storage?.persist?.().catch(() => undefined);
        // First visit: the first screen's art loads BEFORE the worker takes
        // control, so none of it reached the art cache — offline, that very
        // screen came back with holes. Once controlled, hand the worker every
        // art URL this page already fetched so it can store them.
        const warm = () => {
          const ctrl = navigator.serviceWorker.controller;
          if (!ctrl) return;
          const base = configuredAssetsBase();
          const seen = new Set<string>();
          for (const e of performance.getEntriesByType("resource")) {
            if (e.name.startsWith(base) || e.name.includes("/assets/")) seen.add(e.name);
          }
          for (const img of Array.from(document.images)) {
            if (img.currentSrc) seen.add(img.currentSrc);
          }
          if (seen.size) ctrl.postMessage({ type: "warm", urls: Array.from(seen) });
        };
        if (navigator.serviceWorker.controller) warm();
        else navigator.serviceWorker.addEventListener("controllerchange", warm, { once: true });
      })
      .catch(() => undefined);
  });
}
