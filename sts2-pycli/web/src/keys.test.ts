import { describe, it, expect } from "vitest";
import { isTextInput } from "./keys";

describe("isTextInput", () => {
  it("is true for input/textarea/select elements", () => {
    expect(isTextInput(document.createElement("input"))).toBe(true);
    expect(isTextInput(document.createElement("textarea"))).toBe(true);
    expect(isTextInput(document.createElement("select"))).toBe(true);
  });
  it("is false for other elements and null", () => {
    expect(isTextInput(document.createElement("div"))).toBe(false);
    expect(isTextInput(null)).toBe(false);
  });
});
