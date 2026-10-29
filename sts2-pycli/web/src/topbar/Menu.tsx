import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from "react";

/**
 * A top-bar dropdown: one trigger button, a popover of `role="menuitem"` rows.
 *
 * Kept deliberately small — no portal, no positioning library. The popover is
 * absolutely positioned under its trigger inside the bar, which already sits
 * above the screen column (the phone-landscape sheet raises the whole bar,
 * so the menu inherits that too).
 *
 * Keyboard: Enter/Space/ArrowDown on the trigger open and focus the first
 * enabled item; ArrowUp/Down cycle; Home/End jump; Escape closes and returns
 * focus to the trigger; Tab closes (focus moves on naturally). A click
 * outside closes. Disabled items stay in the tab ring's visual list but are
 * skipped by arrow navigation and carry their reason as `title`.
 */

export interface MenuItemSpec {
  id: string;
  label: string;
  title?: string;
  disabledReason?: string;
  danger?: boolean;
  busy?: boolean;
  /** Persistent on/off state, shown as a check mark and `aria-checked`. */
  checked?: boolean;
  onSelect?: () => void;
  /** An external link: rendered as an anchor (new tab), still a menuitem. */
  href?: string;
  /** A control with its own state (a slider, a cycler, a progress button)
   *  hosted in the menu as a plain row — outside the menuitem ring, reached
   *  by Tab, and selecting it does not close the menu. */
  render?: ReactNode;
}

export interface MenuProps {
  label: ReactNode;
  title?: string;
  /** `null` entries render as a separator. */
  items: (MenuItemSpec | null)[];
  /** Accent the trigger (a menu whose contents carry live state). */
  active?: boolean;
  className?: string;
}

export default function Menu({ label, title, items, active, className }: MenuProps) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const id = useId();

  const close = useCallback((refocus = false) => {
    setOpen(false);
    if (refocus) trigger.current?.focus();
  }, []);

  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent | TouchEvent) {
      if (root.current && !root.current.contains(e.target as Node)) close();
    }
    document.addEventListener("mousedown", onDown);
    document.addEventListener("touchstart", onDown);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("touchstart", onDown);
    };
  }, [open, close]);

  function enabledItems(): HTMLElement[] {
    return Array.from(list.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"]), [role="menuitemcheckbox"]:not([aria-disabled="true"])') ?? []);
  }
  function focusAt(i: number) {
    const els = enabledItems();
    if (!els.length) return;
    els[((i % els.length) + els.length) % els.length].focus();
  }
  function openAndFocus(i: number) {
    setOpen(true);
    // The list mounts on the next render.
    requestAnimationFrame(() => focusAt(i));
  }

  function onTriggerKey(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown" || e.key === "Enter" || e.key === " ") { e.preventDefault(); openAndFocus(0); }
    else if (e.key === "ArrowUp") { e.preventDefault(); openAndFocus(-1); }
    else if (e.key === "Escape" && open) { e.preventDefault(); close(true); }
  }
  function onListKey(e: React.KeyboardEvent) {
    const els = enabledItems();
    const cur = els.indexOf(document.activeElement as HTMLElement);
    if (e.key === "ArrowDown") { e.preventDefault(); focusAt(cur + 1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); focusAt(cur - 1); }
    else if (e.key === "Home") { e.preventDefault(); focusAt(0); }
    else if (e.key === "End") { e.preventDefault(); focusAt(-1); }
    else if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); close(true); }
  }
  // Tab (or a click on another control) moves focus out of the menu: close
  // it. Focus moving BETWEEN the menu's own rows — a hosted slider, say —
  // stays inside `root` and keeps it open.
  function onBlur(e: React.FocusEvent) {
    if (open && !root.current?.contains(e.relatedTarget as Node | null)) close();
  }

  return (
    <div ref={root} className={`wb-menu${className ? ` ${className}` : ""}`} onBlur={onBlur}>
      <button ref={trigger} type="button" title={title}
        aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined}
        aria-pressed={active || undefined}
        className={active ? "wb-menu-trigger wb-menu-trigger--active" : "wb-menu-trigger"}
        onClick={() => (open ? close() : setOpen(true))}
        onKeyDown={onTriggerKey}>
        {label} <span aria-hidden="true" className="wb-menu-caret">▾</span>
      </button>
      {open && (
        <div ref={list} id={id} role="menu" className="mono wb-menu-list" onKeyDown={onListKey}>
          {items.map((it, i) => {
            if (it === null) return <div key={`sep-${i}`} role="separator" className="wb-menu-sep" />;
            if (it.render) {
              return <div key={it.id} role="none" className="wb-menu-row" data-command={it.id}>{it.render}</div>;
            }
            const disabled = !!it.disabledReason || !!it.busy;
            const pick = () => {
              if (disabled) return;
              it.onSelect?.();
              close(true);
            };
            const role = it.checked === undefined ? "menuitem" : "menuitemcheckbox";
            if (it.href) {
              return (
                <a key={it.id} role="menuitem" href={it.href} target="_blank" rel="noopener"
                  data-command={it.id} title={it.title} className="wb-menu-item"
                  onClick={() => close()}>
                  <span className="wb-menu-check" aria-hidden="true" />
                  <span className="wb-menu-label">{it.label}</span>
                </a>
              );
            }
            return (
              <div key={it.id} role={role} tabIndex={disabled ? -1 : 0}
                aria-disabled={disabled || undefined}
                aria-checked={it.checked === undefined ? undefined : it.checked}
                data-command={it.id}
                title={it.disabledReason ?? it.title}
                className={[
                  "wb-menu-item",
                  disabled ? "wb-menu-item--disabled" : "",
                  it.danger ? "wb-menu-item--danger" : "",
                  it.checked ? "wb-menu-item--checked" : "",
                ].filter(Boolean).join(" ")}
                onClick={pick}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(); } }}>
                <span className="wb-menu-check" aria-hidden="true">{it.checked ? "✓" : ""}</span>
                <span className="wb-menu-label">{it.busy ? `${it.label}…` : it.label}</span>
                {it.disabledReason && <span className="wb-menu-reason">{it.disabledReason}</span>}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
