import { useEffect, useId, useMemo, useRef, useState } from "react";
import { searchOptions, type Option } from "./catalog";

/**
 * An id picker: type to filter `options` by id or display name, pick with the
 * mouse or ↑/↓ + Enter. The committed value is always an id (what the engine
 * wants); the display name rides beside it in the row. Free text is kept as
 * typed on blur so an id the catalog does not know still reaches the server,
 * whose error then names it.
 */
export default function Combobox({
  value,
  options,
  onChange,
  ariaLabel,
  placeholder,
  width = 220,
  autoFocus,
}: {
  value: string;
  options: Option[];
  onChange: (id: string) => void;
  ariaLabel: string;
  placeholder?: string;
  width?: number | string;
  autoFocus?: boolean;
}) {
  const [text, setText] = useState(value);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const listId = useId();
  const root = useRef<HTMLDivElement>(null);

  // Track outside edits (a preset load, a JSON apply) while not typing.
  useEffect(() => {
    if (!open) setText(value);
  }, [value, open]);

  const matches = useMemo(() => (open ? searchOptions(options, text) : []), [open, options, text]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) commit();
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, text]);

  function pick(id: string) {
    setText(id);
    setOpen(false);
    if (id !== value) onChange(id);
  }
  function commit() {
    setOpen(false);
    const typed = text.trim();
    if (!typed) {
      setText(value);
      return;
    }
    // An exact name match is as good as an id: "Strike" → the id under the caret.
    const exact = options.find((o) => o.id === typed || o.id === typed.toUpperCase() || o.name.toLowerCase() === typed.toLowerCase());
    const next = exact ? exact.id : matches[0] && matches[0].name.toLowerCase().startsWith(typed.toLowerCase()) ? matches[0].id : typed;
    setText(next);
    if (next !== value) onChange(next);
  }

  return (
    <div ref={root} className="sl-combo" style={{ width }}>
      <input
        className="mono"
        value={text}
        aria-label={ariaLabel}
        placeholder={placeholder}
        autoFocus={autoFocus}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        spellCheck={false}
        onFocus={() => {
          setOpen(true);
          setActive(0);
        }}
        onChange={(e) => {
          setText(e.target.value);
          setOpen(true);
          setActive(0);
        }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setOpen(true);
            setActive((a) => Math.min(a + 1, Math.max(0, matches.length - 1)));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActive((a) => Math.max(0, a - 1));
          } else if (e.key === "Enter") {
            e.preventDefault();
            if (open && matches[active]) pick(matches[active].id);
            else commit();
          } else if (e.key === "Escape") {
            setText(value);
            setOpen(false);
          } else if (e.key === "Tab") {
            commit();
          }
        }}
      />
      {open && matches.length > 0 && (
        <ul id={listId} role="listbox" className="sl-combo-list">
          {matches.map((o, i) => (
            <li
              key={o.id}
              role="option"
              aria-selected={i === active}
              className={i === active ? "active" : undefined}
              onMouseDown={(e) => {
                e.preventDefault();
                pick(o.id);
              }}
              onMouseEnter={() => setActive(i)}
            >
              <span className="mono sl-combo-id">{o.id}</span>
              <span className="sl-combo-name">{o.name}</span>
              {o.hint && <span className="sl-combo-hint">{o.hint}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
