/**
 * Message envelopes between the page (`workerClient.ts`) and the engine
 * worker (`../worker/engine.worker.ts`). Shared so both sides type-check
 * against one definition without the page project pulling in worker code.
 *
 *   page -> worker   {init: {assets_base}}            once, right after spawn
 *   page -> worker   {id, method, route, body}        one request
 *   worker -> page   {id, progress: {done, total}}    0..n frames per request
 *   worker -> page   {id, frame: <object>}            0..n per STREAMING request
 *                                                     (one NDJSON line each)
 *   worker -> page   {id, status, body}               exactly one per request
 */
export interface InitMessage {
  init: { assets_base: string };
}
export interface RequestMessage {
  id: number;
  method: string;
  route: string;
  body: unknown;
}
export interface ReplyMessage {
  id: number;
  status: number;
  body: unknown;
}
export interface ProgressMessage {
  id: number;
  progress: { done: number; total: number };
}
/** One line of a streaming (NDJSON) route's body, already parsed. */
export interface FrameMessage {
  id: number;
  frame: unknown;
}
export type WorkerInbound = InitMessage | RequestMessage;
export type WorkerOutbound = ReplyMessage | ProgressMessage | FrameMessage;
