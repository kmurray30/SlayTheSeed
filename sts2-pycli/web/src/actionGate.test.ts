import { describe, it, expect } from "vitest";
import { createActionGate } from "./actionGate";

/** A promise plus the handle to settle it — lets a test hold an "in-flight"
 *  action open for exactly as long as it wants to poke at the gate. */
function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

describe("createActionGate", () => {
  it("runs an action when idle and hands back its result", async () => {
    const gate = createActionGate();
    expect(gate.busy).toBe(false);
    await expect(gate.run(async () => "step")).resolves.toBe("step");
    expect(gate.busy).toBe(false);
  });

  it("drops a second action without invoking it while one is in flight", async () => {
    // The bug: the map stays clickable for the whole round trip, so a second
    // click re-sent `select_map_node` into an engine already in combat.
    const gate = createActionGate();
    const first = deferred<string>();
    let secondRan = false;

    const a = gate.run(() => first.promise);
    expect(gate.busy).toBe(true);
    const b = await gate.run(async () => { secondRan = true; return "second"; });

    expect(secondRan).toBe(false); // never reached the network at all
    expect(b).toBeNull();
    first.resolve("first");
    await expect(a).resolves.toBe("first");
  });

  it("re-opens once the in-flight action lands", async () => {
    const gate = createActionGate();
    const first = deferred<string>();
    const a = gate.run(() => first.promise);
    first.resolve("first");
    await a;
    expect(gate.busy).toBe(false);
    await expect(gate.run(async () => "next")).resolves.toBe("next");
  });

  it("releases the gate when the action throws", async () => {
    // A latched-busy gate would wedge the UI far worse than the bug it fixes.
    const gate = createActionGate();
    await expect(gate.run(async () => { throw new Error("network down"); })).rejects.toThrow("network down");
    expect(gate.busy).toBe(false);
    await expect(gate.run(async () => "recovered")).resolves.toBe("recovered");
  });

  it("holds across a multi-step operation via enter(), then re-opens", async () => {
    // `playBestChoice` is ask-then-dispatch: gating only the dispatch would let
    // a re-entry ask about the screen the first press is already leaving.
    const gate = createActionGate();
    const release = gate.enter()!;
    expect(release).toBeTruthy();
    expect(gate.busy).toBe(true);

    expect(gate.enter()).toBeNull(); // a second press gets nothing
    let ran = false;
    expect(await gate.run(async () => { ran = true; return 1; })).toBeNull();
    expect(ran).toBe(false);

    release();
    expect(gate.busy).toBe(false);
    expect(await gate.run(async () => 2)).toBe(2);
  });

  it("ignores a repeated release so the gate is never handed out twice", async () => {
    const gate = createActionGate();
    const release = gate.enter()!;
    release();
    const other = gate.enter()!; // someone else now holds it
    release(); // the first operation unwinding through its own `finally`
    expect(gate.busy).toBe(true); // still the other operation's
    other();
    expect(gate.busy).toBe(false);
  });
});
