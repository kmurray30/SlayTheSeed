import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent,
  type KeyboardEvent as ReactKeyboardEvent } from "react";
import { clamp } from "../panelPrefs";

/** The collapse caret and the drag grip shared by the workbench's two
 *  space-hungry docks — the PPO policy panel under the board and the
 *  "Machine reasoning" region above the run log. The prefs they persist live
 *  in `panelPrefs.ts` (split out so this file exports components only, which
 *  is what fast refresh wants). */

/** The caret that folds a panel down to its header. Deliberately the same
 *  glyph pair the minimap dock uses (▾ / ▸), since it is the same gesture. */
export function CollapseButton({ collapsed, onToggle, label }: {
  collapsed: boolean;
  onToggle: () => void;
  /** What is being folded, for the tooltip and the accessible name. */
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={!collapsed}
      aria-label={`${collapsed ? "expand" : "collapse"} ${label}`}
      title={`${collapsed ? "expand" : "collapse"} ${label}`}
      style={{
        font: "inherit", background: "none", border: "none", color: "var(--muted)",
        cursor: "pointer", padding: 0, width: 14, flex: "0 0 auto",
      }}
    >
      {collapsed ? "▸" : "▾"}
    </button>
  );
}

/**
 * A drag handle on a panel's free edge.
 *
 * `grow: "up"` is a dock at the BOTTOM of its column (the PPO panel): dragging
 * its top edge upward makes it taller. `grow: "down"` is a region at the top of
 * one (the reasoning panel): dragging its bottom edge down does.
 *
 * The drag starts from the height the panel is actually RENDERING (`measure`),
 * not from the stored cap — both panels size to their content up to that cap,
 * so a drag starting from the cap would jump on the first pixel whenever the
 * content is shorter than it.
 *
 * Pointer events, so touch drags it too; and it is a focusable `separator` with
 * arrow-key steps, so it is not a mouse-only affordance.
 */
export function PanelGrip({ grow, measure, onResize, min, max, label }: {
  grow: "up" | "down";
  measure: () => number;
  onResize: (h: number) => void;
  min: number;
  max: number;
  label: string;
}) {
  const drag = useRef<{ y: number; h: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    if (!dragging) return;
    function onMove(e: globalThis.PointerEvent) {
      const s = drag.current;
      if (!s) return;
      const dy = grow === "up" ? s.y - e.clientY : e.clientY - s.y;
      onResize(clamp(s.h + dy, min, max));
    }
    function onUp() {
      drag.current = null;
      setDragging(false);
    }
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onUp);
    };
  }, [dragging, grow, min, max, onResize]);

  function onPointerDown(e: ReactPointerEvent) {
    drag.current = { y: e.clientY, h: measure() };
    setDragging(true);
    e.preventDefault();
  }
  function onKeyDown(e: ReactKeyboardEvent) {
    const step = e.key === "ArrowUp" ? -16 : e.key === "ArrowDown" ? 16 : 0;
    if (!step) return;
    e.preventDefault();
    onResize(clamp(measure() + (grow === "up" ? -step : step), min, max));
  }

  return (
    <div
      className={`wb-grip${dragging ? " wb-grip--dragging" : ""}`}
      role="separator"
      aria-orientation="horizontal"
      aria-label={label}
      title={label}
      tabIndex={0}
      onPointerDown={onPointerDown}
      onKeyDown={onKeyDown}
    />
  );
}
