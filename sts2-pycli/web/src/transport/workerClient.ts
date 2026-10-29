/**
 * Page-side client for the engine worker (`src/worker/engine.worker.ts`).
 *
 * Envelope: `{id, method, route, body}` -> `{id, status, body}`, with any
 * number of `{id, progress: {done, total}}` frames in between, and — for a
 * streaming route — `{id, frame}` messages carrying the NDJSON lines of the
 * body, delivered to the request's `onFrame` as they arrive. Requests are
 * serialised: the wasm is single-threaded, so one is in flight at a time and
 * they complete in call order. The worker is spawned lazily on first use and
 * told `{init: {assets_base}}` first thing.
 */
import type {
  FrameMessage,
  ProgressMessage,
  ReplyMessage,
  RequestMessage,
  WorkerInbound,
  WorkerOutbound,
} from "./protocol";

export interface Progress {
  done: number;
  total: number;
}

export interface Reply {
  status: number;
  body: unknown;
}

export type ProgressListener = (p: Progress, id: number) => void;
export type FrameListener = (frame: unknown) => void;

/** The subset of `Worker` the client needs (tests hand in a fake). */
export interface WorkerLike {
  postMessage(msg: WorkerInbound): void;
  onmessage: ((ev: MessageEvent<WorkerOutbound>) => void) | null;
  onerror: ((ev: ErrorEvent) => void) | null;
  terminate(): void;
}

export interface WorkerClientOptions {
  spawn: () => WorkerLike;
  assetsBase: string;
}

interface Pending {
  req: RequestMessage;
  onFrame?: FrameListener;
  resolve: (r: Reply) => void;
  reject: (e: Error) => void;
}

export class WorkerClient {
  private worker: WorkerLike | null = null;
  private nextId = 1;
  private queue: Pending[] = [];
  private inFlight: Pending | null = null;
  private listeners = new Set<ProgressListener>();

  constructor(private readonly opts: WorkerClientOptions) {}

  /** Send one request; resolves with the worker's `{status, body}`. A
   *  streaming route's `{id, frame}` messages go to `onFrame`, in order,
   *  before the reply settles the promise. */
  request(
    method: string,
    route: string,
    body: unknown = null,
    onFrame?: FrameListener,
  ): Promise<Reply> {
    return new Promise<Reply>((resolve, reject) => {
      this.queue.push({
        req: { id: this.nextId++, method, route, body },
        onFrame,
        resolve,
        reject,
      });
      this.pump();
    });
  }

  /** Subscribe to progress frames (any request); returns the unsubscribe. */
  onProgress(cb: ProgressListener): () => void {
    this.listeners.add(cb);
    return () => void this.listeners.delete(cb);
  }

  private ensureWorker(): WorkerLike {
    if (this.worker) return this.worker;
    const w = this.opts.spawn();
    w.onmessage = (ev) => this.onMessage(ev.data);
    w.onerror = (ev) => this.fail(new Error(`engine worker crashed: ${ev.message ?? ev}`));
    w.postMessage({ init: { assets_base: this.opts.assetsBase } });
    this.worker = w;
    return w;
  }

  private pump(): void {
    if (this.inFlight) return;
    const next = this.queue.shift();
    if (!next) return;
    this.inFlight = next;
    this.ensureWorker().postMessage(next.req);
  }

  private onMessage(msg: WorkerOutbound): void {
    const cur = this.inFlight;
    if (!cur || msg.id !== cur.req.id) return; // stale frame from a dropped worker
    if ("progress" in msg) {
      const p = (msg as ProgressMessage).progress;
      for (const l of this.listeners) l(p, msg.id);
      return;
    }
    if ("frame" in msg) {
      cur.onFrame?.((msg as FrameMessage).frame);
      return;
    }
    const { status, body } = msg as ReplyMessage;
    this.inFlight = null;
    cur.resolve({ status, body });
    this.pump();
  }

  /** A dead worker rejects everything outstanding; the next request respawns. */
  private fail(err: Error): void {
    const all = [this.inFlight, ...this.queue].filter((p): p is Pending => !!p);
    this.inFlight = null;
    this.queue = [];
    this.worker?.terminate();
    this.worker = null;
    for (const p of all) p.reject(err);
  }
}

let singleton: WorkerClient | null = null;

/** The page's one engine client (spawns the worker on first request). */
export function getWorkerClient(assetsBase: string): WorkerClient {
  singleton ??= new WorkerClient({
    spawn: () =>
      new Worker(new URL("../worker/engine.worker.ts", import.meta.url), { type: "module" }),
    assetsBase,
  });
  return singleton;
}
