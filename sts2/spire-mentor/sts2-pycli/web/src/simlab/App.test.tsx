import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";

import { CATALOG, META, makeApiMocks } from "./testMocks";

// Mock the typed api client: meta drives the form; submit captures the spec.
const mocks = vi.hoisted(() => ({ api: null as unknown as ReturnType<typeof import("./testMocks").makeApiMocks> }));
vi.mock("./api", () => ({
  get api() {
    return mocks.api;
  },
}));
mocks.api = makeApiMocks();
const submit = mocks.api.submit;
const meta = mocks.api.meta;
const list = mocks.api.list;
void CATALOG;

import App from "./App";

beforeEach(() => {
  submit.mockClear();
  list.mockClear();
  meta.mockReset();
  meta.mockResolvedValue(META);
});

const ta = (n: number) => screen.getByLabelText(`variant ${n} cards`) as HTMLTextAreaElement;
const nameInput = (n: number) =>
  screen.getByLabelText(`variant ${n} name`) as HTMLInputElement;

async function waitForMeta() {
  // The batch form lives on the Studies tab; the sandbox is the default.
  fireEvent.click(await screen.findByRole("tab", { name: "Studies" }));
  // The first variant's textarea is seeded from the ironclad_starter preset.
  await waitFor(() => expect(ta(1).value).toContain("5x STRIKE_IRONCLAD"));
}

describe("SweepForm deck variants", () => {
  it("seeds the first variant from the ironclad_starter preset", async () => {
    render(<App />);
    await waitForMeta();
    expect(nameInput(1).value).toBe("ironclad_starter");
  });

  it("+ add variant clones the last variant with a -2 suffixed name", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.click(screen.getByText("+ add variant"));
    expect(nameInput(2).value).toBe("ironclad_starter-2");
    expect(ta(2).value).toContain("5x STRIKE_IRONCLAD");
  });

  it("disables remove when only one variant, enables with more", async () => {
    render(<App />);
    await waitForMeta();
    expect(screen.getByLabelText("remove variant 1")).toBeDisabled();
    fireEvent.click(screen.getByText("+ add variant"));
    expect(screen.getByLabelText("remove variant 1")).toBeEnabled();
    fireEvent.click(screen.getByLabelText("remove variant 2"));
    expect(screen.queryByLabelText("variant 2 name")).toBeNull();
  });

  it("flags unknown cards and blocks Run", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(ta(1), { target: { value: "BOGUS_CARD" } });
    expect(await screen.findByText(/unknown card/i)).toBeInTheDocument();
    expect(screen.getByText("Run sweep")).toBeDisabled();
  });

  it("flags a deck larger than 100 cards and blocks Run", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(ta(1), { target: { value: "101x STRIKE_IRONCLAD" } });
    expect(await screen.findByText(/deck too large \(101\/100\)/i)).toBeInTheDocument();
    expect(screen.getByText("Run sweep")).toBeDisabled();
  });

  it("flags duplicate variant names and blocks Run", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.click(screen.getByText("+ add variant"));
    fireEvent.change(nameInput(2), { target: { value: "ironclad_starter" } });
    // Both colliding variants surface the hint.
    expect((await screen.findAllByText(/duplicate name/i)).length).toBeGreaterThan(0);
    expect(screen.getByText("Run sweep")).toBeDisabled();
  });

  it("flags a name containing a slash", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(nameInput(1), { target: { value: "a/b" } });
    expect(await screen.findByText(/may not contain/i)).toBeInTheDocument();
  });

  it("load-preset select fills the textarea", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(screen.getByLabelText("variant 1 load preset"), {
      target: { value: "aggro_rush" },
    });
    expect(ta(1).value).toBe("STRIKE_IRONCLAD\nBASH");
  });

  it("submits decks as [name, cards][] pairs (no deck_preset)", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.click(screen.getByText("Run sweep"));
    await waitFor(() => expect(submit).toHaveBeenCalled());
    const spec = submit.mock.calls[0][0] as {
      decks: [string, string[]][];
      deck_preset?: string;
    };
    expect(spec.decks).toHaveLength(1);
    expect(spec.decks[0][0]).toBe("ironclad_starter");
    expect(spec.decks[0][1]).toHaveLength(10);
    expect(spec.decks[0][1].filter((c) => c === "STRIKE_IRONCLAD")).toHaveLength(5);
    expect(spec.deck_preset).toBeUndefined();
  });
});

describe("SweepForm custom enemies", () => {
  it("adds a custom enemy chip and includes it in submitted encounters", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(screen.getByLabelText("custom enemy"), { target: { value: "Nibbit" } });
    fireEvent.change(screen.getByLabelText("custom enemy count"), { target: { value: "2" } });
    fireEvent.click(screen.getByText("add"));
    expect(await screen.findByText("Nibbit ×2")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Run sweep"));
    await waitFor(() => expect(submit).toHaveBeenCalled());
    const spec = submit.mock.calls[0][0] as { encounters: string[] };
    expect(spec.encounters).toContain("monsters:Nibbit,Nibbit");
  });

  it("removes a custom enemy chip", async () => {
    render(<App />);
    await waitForMeta();
    fireEvent.change(screen.getByLabelText("custom enemy"), { target: { value: "Nibbit" } });
    fireEvent.click(screen.getByText("add"));
    const remove = await screen.findByLabelText("remove monsters:Nibbit");
    fireEvent.click(remove);
    expect(screen.queryByLabelText("remove monsters:Nibbit")).toBeNull();
  });
});
