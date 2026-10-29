/**
 * Minimal promise wrapper over IndexedDB for the engine worker — no library.
 *
 * One database, `sts2-workbench`, with two key/value stores (out-of-line keys):
 *   - `saves`: named run saves (key = slugified name, value = the payload
 *     `Workbench.build_save` produced), what `/api/saves` lists and loads.
 *   - `live`: the persisted in-progress run under key `"current"`, rewritten
 *     after every mutating call so a reload / Android process kill resumes it.
 *   - `runs`: the JSONL run logs finished runs leave behind (key = the
 *     filename `Workbench.take_run_log` names, value = `{text, size, mtime}`),
 *     what `/api/runs` lists and `/api/load` reopens in the replay viewer.
 *   - `sweeps`: SimLab distribution sweeps (key = sweep id, value = the
 *     document `Workbench.export_sweep` emits: spec, rows, progress), so the
 *     Studies history survives a reload (GH #707).
 *
 * `KvStore` is the seam `engineRoutes.ts` codes against; tests substitute a
 * Map-backed one (jsdom has no IndexedDB).
 */
export type StoreName = "saves" | "live" | "runs" | "sweeps";

export interface KvStore {
  get<T = unknown>(store: StoreName, key: string): Promise<T | undefined>;
  getAll<T = unknown>(store: StoreName): Promise<T[]>;
  /** Every key in the store (a listing without the values). */
  keys(store: StoreName): Promise<string[]>;
  put(store: StoreName, key: string, value: unknown): Promise<void>;
  delete(store: StoreName, key: string): Promise<void>;
}

export const DB_NAME = "sts2-workbench";
// v2 added `runs` (M4), v3 `sweeps` (GH #707); `onupgradeneeded` only creates
// what is missing.
const DB_VERSION = 3;
const STORES: StoreName[] = ["saves", "live", "runs", "sweeps"];

function request<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB request failed"));
  });
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      for (const name of STORES) {
        if (!db.objectStoreNames.contains(name)) db.createObjectStore(name);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error(`cannot open ${DB_NAME}`));
    req.onblocked = () => reject(new Error(`${DB_NAME} is blocked by another tab`));
  });
}

/** Open (lazily, once) the workbench database as a `KvStore`. */
export function openKv(): KvStore {
  let dbp: Promise<IDBDatabase> | null = null;
  const db = () => (dbp ??= openDb());
  const tx = async (store: StoreName, mode: IDBTransactionMode) =>
    (await db()).transaction(store, mode).objectStore(store);
  return {
    async get<T>(store: StoreName, key: string) {
      return request<T | undefined>((await tx(store, "readonly")).get(key));
    },
    async getAll<T>(store: StoreName) {
      return request<T[]>((await tx(store, "readonly")).getAll());
    },
    async keys(store: StoreName) {
      const ks = await request<IDBValidKey[]>((await tx(store, "readonly")).getAllKeys());
      return ks.map(String);
    },
    async put(store, key, value) {
      await request((await tx(store, "readwrite")).put(value, key));
    },
    async delete(store, key) {
      await request((await tx(store, "readwrite")).delete(key));
    },
  };
}

/** Map-backed `KvStore` (tests; also a fallback when IndexedDB is unavailable). */
export function memoryKv(): KvStore {
  const stores: Record<StoreName, Map<string, unknown>> = {
    saves: new Map(),
    live: new Map(),
    runs: new Map(),
    sweeps: new Map(),
  };
  return {
    async get<T>(store: StoreName, key: string) {
      return stores[store].get(key) as T | undefined;
    },
    async getAll<T>(store: StoreName) {
      return [...stores[store].values()] as T[];
    },
    async keys(store: StoreName) {
      return [...stores[store].keys()];
    },
    async put(store, key, value) {
      stores[store].set(key, value);
    },
    async delete(store, key) {
      stores[store].delete(key);
    },
  };
}
