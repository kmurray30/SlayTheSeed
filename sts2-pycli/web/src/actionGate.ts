/** Single-slot exclusion for actions applied to the live run.
 *
 * Every dispatch in the workbench is decided against `live.decision` — which
 * map nodes are clickable, which numbered options a keypress resolves, what the
 * policy is asked to recommend. That state only updates once the action's round
 * trip lands, so for the whole duration of a request the UI is still offering,
 * and still willing to send, the choices of a screen the engine has already left
 * behind. Entering a combat room is among the slowest actions there is, which is
 * why the map is where this surfaced (bug wb-12ce3fe830de0bb1: a second
 * `select_map_node` arriving after the first had entered combat, rejected with
 * `invalid_for_phase` and painted as a red engine error).
 *
 * A second gesture in that window is DROPPED, not queued. It was decided against
 * a superseded screen, so replaying it once the first lands is not "the user's
 * other click" — at best it is illegal (the map case), at worst it is legal and
 * means something else entirely, because card indices have shifted under it.
 * Dropping costs one repeated click; queueing costs a wrong move.
 *
 * The gate spans a whole *operation*, not a single request: "ask the policy,
 * then play what it said" is two round trips, and holding it only across the
 * second one still lets a re-entry ask about the screen the first is leaving and
 * dispatch that stale answer. Nest such a call through {@link ActionGate.enter}
 * so the inner dispatch does not deadlock against the gate its own caller holds.
 */
export interface ActionGate {
  /** Whether an operation currently holds the gate. */
  readonly busy: boolean;
  /** Run `fn` exclusively. Returns `null` — without calling `fn` at all — when
   *  another operation is already in flight. */
  run<T>(fn: () => Promise<T>): Promise<T | null>;
  /** Take the gate for a multi-step operation, returning `null` if it is held.
   *  The returned release MUST be called from a `finally`. Work done under it
   *  should call the un-gated inner form of each step, never {@link run}. */
  enter(): (() => void) | null;
}

export function createActionGate(): ActionGate {
  let busy = false;
  return {
    get busy() {
      return busy;
    },
    async run<T>(fn: () => Promise<T>): Promise<T | null> {
      const release = this.enter();
      if (!release) return null;
      try {
        return await fn();
      } finally {
        release();
      }
    },
    enter(): (() => void) | null {
      if (busy) return null;
      busy = true;
      let released = false;
      return () => {
        // Idempotent: a caller that releases early and then unwinds through its
        // own `finally` must not hand the gate to two operations at once.
        if (released) return;
        released = true;
        busy = false;
      };
    },
  };
}
