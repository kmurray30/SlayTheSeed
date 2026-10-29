import { scheduleTimes } from "./timing";
import type { EffectEvent } from "./types";

export interface Clock {
  now(): number;
  /** Schedule *cb* for the next frame; returns a canceller. */
  frame(cb: () => void): () => void;
}

/** The production clock. Tests inject a hand-driven one instead. */
export const rafClock: Clock = {
  now: () => performance.now(),
  frame: (cb) => {
    const id = requestAnimationFrame(() => cb());
    return () => cancelAnimationFrame(id);
  },
};

interface Queued {
  at: number;
  event: EffectEvent;
}

/**
 * Plays an action's effect events out over real time.
 *
 * Input is never blocked in the workbench, so a second action can arrive
 * mid-playback. The rule is SUPERSEDE: `play` drains the outstanding queue —
 * EMITTING its events, not discarding them, because the board state they
 * carry (a death, a power) must still land or the board disagrees with the
 * engine — and then starts the new action.
 */
export class EffectScheduler {
  private queue: Queued[] = [];
  private cancel: (() => void) | null = null;
  private disposed = false;

  constructor(
    private readonly clock: Clock,
    private readonly emit: (e: EffectEvent) => void,
  ) {}

  get pending(): number {
    return this.queue.length;
  }

  play(events: EffectEvent[]): void {
    if (this.disposed) return;
    this.drain();
    if (events.length === 0) return;
    const t0 = this.clock.now();
    const times = scheduleTimes(events);
    this.queue = events.map((event, i) => ({ at: t0 + times[i]!, event }));
    this.pump();
  }

  drain(): void {
    this.stopTimer();
    const due = this.queue;
    this.queue = [];
    for (const q of due) this.emit(q.event);
  }

  dispose(): void {
    this.stopTimer();
    this.queue = [];
    this.disposed = true;
  }

  private stopTimer(): void {
    this.cancel?.();
    this.cancel = null;
  }

  private pump = (): void => {
    if (this.disposed) return;
    this.cancel = null;
    const now = this.clock.now();
    while (this.queue.length > 0 && this.queue[0]!.at <= now) {
      this.emit(this.queue.shift()!.event);
    }
    if (this.queue.length > 0) this.cancel = this.clock.frame(this.pump);
  };
}
