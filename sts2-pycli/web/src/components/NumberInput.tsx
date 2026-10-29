import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";

/**
 * A compact numeric field with its own stepper buttons.
 *
 * The native `<input type="number">` spinners are tiny and, in the narrow
 * fields the workbench uses, the browser draws them right on top of the digits
 * — you can't read the value you're editing. This hides the native spinners
 * (see `.numinput input` in theme.css) and renders our own stacked ▲/▼ buttons
 * beside the field, sized to fit and never overlapping the number.
 *
 * The field keeps a local text buffer while you edit so it can hold transient
 * states the committed number can't — an empty field, or a half-typed value —
 * without snapping back. If the input were controlled directly by the numeric
 * `value`, clearing it (to type, say, 3000 over 1000) would bounce straight to
 * `min` the moment the last digit is deleted, and you could never empty it. We
 * commit a clamped number whenever the buffer parses, and normalize the text on
 * blur (empty/invalid reverts to the current value).
 */
export default function NumberInput({
  value,
  onChange,
  min,
  max,
  step = 1,
  width = 56,
  disabled,
  onKeyDown,
  ariaLabel,
}: {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  width?: number | string;
  disabled?: boolean;
  onKeyDown?: (e: KeyboardEvent<HTMLInputElement>) => void;
  ariaLabel?: string;
}) {
  const clamp = (n: number) => {
    if (min !== undefined) n = Math.max(min, n);
    if (max !== undefined) n = Math.min(max, n);
    return n;
  };
  // Round to the step's decimal places so 1.4 + 0.1 stays 1.5, not 1.4999….
  const decimals = (String(step).split(".")[1] ?? "").length;
  const snap = (n: number) => Number(n.toFixed(decimals));
  const bump = (dir: 1 | -1) => onChange(clamp(snap(value + dir * step)));

  const [text, setText] = useState(String(value));
  const focused = useRef(false);

  // Mirror external value changes (stepper buttons, programmatic resets) into
  // the buffer, but never while the user is mid-edit or we'd clobber typing.
  useEffect(() => {
    if (!focused.current) setText(String(value));
  }, [value]);

  return (
    <span className="numinput" style={{ width }}>
      <input
        type="number"
        value={text}
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        aria-label={ariaLabel}
        onKeyDown={onKeyDown}
        onFocus={() => {
          focused.current = true;
        }}
        onBlur={() => {
          focused.current = false;
          const n = Number(text);
          if (text.trim() === "" || Number.isNaN(n)) {
            setText(String(value)); // revert empty/invalid to the live value
          } else {
            const committed = clamp(snap(n));
            setText(String(committed));
            if (committed !== value) onChange(committed);
          }
        }}
        onChange={(e) => {
          const raw = e.target.value;
          setText(raw);
          // Commit only when the buffer is a real number; leave empty/partial
          // states (e.g. "" or "-") alone so the field can be cleared.
          if (raw.trim() === "") return;
          const n = Number(raw);
          if (!Number.isNaN(n)) onChange(clamp(n));
        }}
      />
      <span className="numinput-steppers">
        <button type="button" tabIndex={-1} disabled={disabled || (max !== undefined && value >= max)}
          aria-label="increment" onClick={() => bump(1)}>▲</button>
        <button type="button" tabIndex={-1} disabled={disabled || (min !== undefined && value <= min)}
          aria-label="decrement" onClick={() => bump(-1)}>▼</button>
      </span>
    </span>
  );
}
