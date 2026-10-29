// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { PLACEHOLDER_DATA_URI, applyPlaceholder, installArtPlaceholder, isArtUrl } from "./artPlaceholder";

describe("isArtUrl", () => {
  it("matches only files under the art base", () => {
    expect(isArtUrl("/assets/card_atlas/ironclad/strike.png", "/assets")).toBe(true);
    expect(isArtUrl("http://localhost:5173/assets/monsters/nibbit.png", "/assets")).toBe(true);
    expect(isArtUrl("/icons/icon-192.png", "/assets")).toBe(false);
    expect(isArtUrl("data:image/svg+xml;utf8,x", "/assets")).toBe(false);
    expect(isArtUrl("", "/assets")).toBe(false);
  });

  it("handles an absolute art origin", () => {
    expect(isArtUrl("https://cdn.example/art/relics/anchor.png", "https://cdn.example/art")).toBe(true);
    expect(isArtUrl("/assets/relics/anchor.png", "https://cdn.example/art")).toBe(false);
  });
});

describe("applyPlaceholder", () => {
  it("swaps a failed art image once and leaves non-art alone", () => {
    const img = document.createElement("img");
    img.setAttribute("src", "/assets/relics/anchor.png");
    expect(applyPlaceholder(img, "/assets")).toBe(true);
    expect(img.src).toBe(PLACEHOLDER_DATA_URI);
    expect(applyPlaceholder(img, "/assets")).toBe(false); // no loop on the placeholder itself

    const icon = document.createElement("img");
    icon.setAttribute("src", "/icons/icon-192.png");
    expect(applyPlaceholder(icon, "/assets")).toBe(false);
    expect(icon.getAttribute("src")).toBe("/icons/icon-192.png");
  });
});

describe("installArtPlaceholder", () => {
  it("catches non-bubbling img error events at the document", () => {
    installArtPlaceholder(document, () => "/assets");
    installArtPlaceholder(document, () => "/assets"); // idempotent
    const img = document.createElement("img");
    img.setAttribute("src", "/assets/card_atlas/ironclad/bash.png");
    document.body.appendChild(img);
    img.dispatchEvent(new Event("error", { bubbles: false }));
    expect(img.src).toBe(PLACEHOLDER_DATA_URI);
  });
});
