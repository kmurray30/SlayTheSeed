import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type EffectsMode = "full" | "reduced" | "off";

export function resolveMode(opts: { toggle: EffectsMode; autoplaying: boolean; reducedMotion: boolean }): EffectsMode {
  if (opts.toggle === "off") return "off";
  // Autoplay/fast-step: keep attack/hurt/death cues playing but calm the big
  // motion (screen shake) — the loop is paced by the per-action server round
  // trip, so the one-shots are watchable. Set the fx toggle to "off" for a
  // truly silent fast-step.
  if (opts.autoplaying) return "reduced";
  if (opts.reducedMotion) return "reduced"; // still show informative cues; motion is calmed
  return opts.toggle;
}

interface EffectsValue {
  mode: EffectsMode;
  toggle: EffectsMode;
  setToggle: (m: EffectsMode) => void;
}

const Ctx = createContext<EffectsValue>({ mode: "full", toggle: "full", setToggle: () => {} });

export function useEffectsMode(): EffectsValue {
  return useContext(Ctx);
}

export function EffectsProvider({ autoplaying, children }: { autoplaying: boolean; children: ReactNode }) {
  const [toggle, setToggle] = useState<EffectsMode>("full");
  const [reducedMotion, setReducedMotion] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    if (!mq) return;
    const on = () => setReducedMotion(mq.matches);
    on();
    mq.addEventListener?.("change", on);
    return () => mq.removeEventListener?.("change", on);
  }, []);
  const mode = resolveMode({ toggle, autoplaying, reducedMotion });
  const value = useMemo(() => ({ mode, toggle, setToggle }), [mode, toggle]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
