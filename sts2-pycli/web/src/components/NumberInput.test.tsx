import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { useState } from "react";
import NumberInput from "./NumberInput";

/** Controlled wrapper mirroring how the workbench drives the field. */
function Harness({
  initial,
  min,
  max,
  onChange,
}: {
  initial: number;
  min?: number;
  max?: number;
  onChange?: (n: number) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <NumberInput
      value={value}
      min={min}
      max={max}
      ariaLabel="sims"
      onChange={(n) => {
        setValue(n);
        onChange?.(n);
      }}
    />
  );
}

describe("NumberInput editing", () => {
  it("lets you clear the field even when min > 0", () => {
    render(<Harness initial={1000} min={1} max={20000} />);
    const input = screen.getByLabelText("sims") as HTMLInputElement;
    // The whole point: deleting the last digit must leave the field empty
    // instead of snapping back to `min`.
    fireEvent.change(input, { target: { value: "" } });
    expect(input.value).toBe("");
  });

  it("can replace 1000 with 3000 by clearing then typing", () => {
    const onChange = vi.fn();
    render(<Harness initial={1000} min={1} max={20000} onChange={onChange} />);
    const input = screen.getByLabelText("sims") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.change(input, { target: { value: "3" } });
    fireEvent.change(input, { target: { value: "30" } });
    fireEvent.change(input, { target: { value: "300" } });
    fireEvent.change(input, { target: { value: "3000" } });
    expect(input.value).toBe("3000");
    expect(onChange).toHaveBeenLastCalledWith(3000);
  });

  it("reverts an empty field to the live value on blur", () => {
    render(<Harness initial={1000} min={1} max={20000} />);
    const input = screen.getByLabelText("sims") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.blur(input);
    expect(input.value).toBe("1000");
  });

  it("clamps to max on blur", () => {
    const onChange = vi.fn();
    render(<Harness initial={1000} min={1} max={20000} onChange={onChange} />);
    const input = screen.getByLabelText("sims") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "99999" } });
    fireEvent.blur(input);
    expect(input.value).toBe("20000");
    expect(onChange).toHaveBeenLastCalledWith(20000);
  });
});
