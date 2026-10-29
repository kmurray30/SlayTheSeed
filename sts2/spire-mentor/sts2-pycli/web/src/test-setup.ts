import "@testing-library/jest-dom";

// jsdom does not implement scrollIntoView; stub it so components that autoscroll
// (e.g. LiveLog) don't throw under test.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// jsdom has no ResizeObserver. Components that size a canvas to its container
// (the Kaiser Crab stage layer) construct one on mount; without this they throw
// during layout effects and take the whole render down. The stub observes
// nothing, which is right for jsdom — every box there measures 0 anyway, so the
// component's own fallback size is what a test should see.
if (typeof globalThis.ResizeObserver === "undefined") {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

// Node 22+ defines its own `globalThis.localStorage`, and without a
// `--localstorage-file` path it is a bare object with none of the Storage
// methods. Under vitest's jsdom environment that global already exists when
// jsdom installs its own, so jsdom's real implementation never lands and
// `localStorage.clear()` throws `TypeError: clear is not a function` — taking
// down every `beforeEach(() => localStorage.clear())`. A throwing `beforeEach`
// also skips cleanup, so the previous test's DOM stays mounted and the next
// assertion fails with "Found multiple elements" instead of anything that
// names the real cause. Install a working in-memory Storage when the ambient
// one is unusable; leave a real implementation (a browser, or a Node started
// with a storage file) alone.
if (typeof globalThis.localStorage?.clear !== "function") {
  const store = new Map<string, string>();
  const storage: Storage = {
    get length() {
      return store.size;
    },
    key: (i: number) => [...store.keys()][i] ?? null,
    getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
    setItem: (k: string, v: string) => void store.set(k, String(v)),
    removeItem: (k: string) => void store.delete(k),
    clear: () => store.clear(),
  };
  Object.defineProperty(globalThis, "localStorage", {
    value: storage,
    configurable: true,
    writable: true,
  });
}
