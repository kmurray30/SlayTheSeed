import { test, expect } from "@playwright/test";
import { menuItem } from "../e2e/topbarMenu";

/**
 * The SimLab position sandbox in the browser-only build: the wasm engine
 * answers `/api/simlab/*`, so the page can deal a fight, build/validate it, and
 * run the per-move MCTS search with no Python server.
 *
 * The distribution sweeps run in the wasm too since GH #707 (the worker steps
 * the job between requests), so the Studies tab and the "play it out" card are
 * offered and a small study runs to completion here. Graft/position
 * correctness is the Rust suite's job (`simlab::tests`, `simlab_smoke`,
 * `simlab_sweep::tests`, the `sweep_parity` golden); this owns the page path
 * end to end.
 */

test("the sandbox deals a fight, offers the sweeps, and searches in the wasm", async ({ page }) => {
  await page.goto("/simlab.html");
  await expect(page.getByRole("heading", { name: "SimLab" })).toBeVisible();

  // Both tabs are here: the sandbox and the sweep-backed Studies.
  await expect(page.getByRole("tab", { name: "Sandbox" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Studies" })).toBeVisible();

  // The page auto-deals a fight and the wasm engine builds it.
  await expect(page.getByText("Built.")).toBeVisible({ timeout: 60_000 });

  // The distribution "play it out" sweep is offered (it runs in the worker).
  await expect(page.getByRole("button", { name: "play it out" })).toBeVisible();

  // The per-move MCTS search runs in the wasm and returns a ranked verdict.
  await page.getByRole("button", { name: "search this position" }).click();
  await expect(page.getByRole("columnheader", { name: "win %" })).toBeVisible({ timeout: 60_000 });
  await expect(page.locator("tr.best")).toBeVisible();
});

/**
 * A study runs to completion in the browser: the worker plays the trials
 * between the page's status polls (the page never freezes — the poll that
 * drives the progress bar is itself a request the worker answers mid-sweep),
 * and the results view renders the buckets once the sweep is done.
 */
test("a small study runs to completion in the worker and renders its results", async ({ page }) => {
  await page.goto("/simlab.html");
  await page.getByRole("tab", { name: "Studies" }).click();
  // /api/meta drives the form: the preset deck lands in the first variant.
  await expect(page.getByLabel("variant 1 cards")).toHaveValue(/STRIKE_IRONCLAD/, { timeout: 60_000 });
  // Two cheap strategies, a handful of seeds — seconds, not minutes.
  const strategies = page.getByRole("group", { name: "Strategies" });
  await strategies.getByLabel("mcts").uncheck();
  await strategies.getByLabel("random").check();
  await strategies.getByLabel("attack_first").check();
  await page.getByLabel("combats per strategy").fill("4");
  await page.getByRole("button", { name: "Run sweep" }).click();
  // The results view: the baseline selector only renders on a finished sweep.
  await expect(page.getByText("Baseline strategy:")).toBeVisible({ timeout: 120_000 });
  await expect(page.getByRole("option", { name: "attack_first" })).toBeAttached();
  // …and the history lists it as done.
  await expect(page.getByText(/4 seeds/)).toBeVisible();
});

/**
 * The sandbox is only useful if you can get to it. The top-bar link used to be
 * gated on `!isStaticMode()` — correct while SimLab was desktop-only, and left
 * behind when the wasm grew the sandbox, so the browser build shipped a page
 * with no way in. It is gated on the `simlab` capability now; this is the guard.
 */
test("the workbench top bar links to the sandbox", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByLabel("seed", { exact: true })).toBeVisible({ timeout: 60_000 });
  // The link lives in the Tools menu (an anchor with the menuitem role).
  const link = await menuItem(page, "Tools", /SimLab/);
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute("href", "/simlab.html");
});
