import { test, expect, type Page } from "@playwright/test";

/**
 * Bug wb-12ce3fe830de0bb1: "⚠ select_map_node rejected by engine:
 * invalid_for_phase".
 *
 * The report's own timeline shows the pick SUCCEEDED — node 28 is
 * `Map → (2,3)` landing in `combat_play` with "Spawned: Seapunk". So a *second*
 * `select_map_node` followed it, by which time the engine was in `Phase::Combat`
 * and rejected it.
 *
 * It got sent because every dispatch decision is made against `live.decision`,
 * which only updates once the round trip lands (`setLive` after
 * `await api.live.action`). Entering a combat room is one of the slowest
 * actions there is, so the window is wide, and during it nothing is disarmed:
 * the map nodes stay clickable, and `keydown` for g/G/a/A keeps firing
 * `playBestChoice` on auto-repeat.
 *
 * These tests widen that window deliberately (`page.route` holds the response)
 * so the race is deterministic rather than a matter of how fast the machine is.
 * Before the fix both send two actions and paint the red banner; after it the
 * second gesture is dropped.
 */

/** Start a manual run at cheap budgets and advance to the first map screen.
 *  The opening screens vary by seed (a starting event, a relic pick, …), so
 *  answer whatever is offered with "1" until the map is the picker. */
async function openMapScreen(page: Page): Promise<void> {
  // One backend is shared by the whole file, so end whatever the previous test
  // left open — otherwise the app boots onto that run instead of the start form.
  await page.request.post("/api/live/quit").catch(() => {});
  await page.goto("/");
  await expect(page.getByLabel("seed", { exact: true })).toBeVisible();
  await page.getByLabel("seed", { exact: true }).fill("12313");
  await page.getByLabel("monster combat sims").fill("20");
  await page.getByLabel("boss combat sims").fill("20");
  await page.getByRole("button", { name: "Play yourself" }).click();

  for (let i = 0; i < 8; i++) {
    if ((await selectableNodes(page).count()) > 0) return;
    await page.keyboard.press("1");
    await page.waitForTimeout(500);
  }
  expect(await selectableNodes(page).count(), "reached a map_select screen").toBeGreaterThan(0);
}

/** The map's own pickable nodes — `data-can` is set only where `selectable`
 *  contains the node's `col,row`, i.e. exactly the legal next floors. */
function selectableNodes(page: Page) {
  return page.locator('g[data-can][role="button"]');
}

/** Count `select_map_node` posts that actually reached the server, and hold the
 *  first one open for `holdMs` so the second gesture lands mid-flight. */
async function holdFirstMapSelect(page: Page, holdMs: number): Promise<() => number> {
  let seen = 0;
  await page.route("**/api/live/action", async (route) => {
    const body = route.request().postDataJSON() as { action?: string } | null;
    if (body?.action !== "select_map_node") return route.continue();
    seen += 1;
    if (seen === 1) await new Promise((r) => setTimeout(r, holdMs));
    return route.continue();
  });
  return () => seen;
}

test("a second map click during the first one's round trip is not sent", async ({ page }) => {
  await openMapScreen(page);
  const count = await holdFirstMapSelect(page, 2500);

  const node = selectableNodes(page).first();
  await node.click();
  // Impatient second click, ~1.5s into a pick the server has not answered yet.
  await page.waitForTimeout(1500);
  await selectableNodes(page).first().click().catch(() => {
    // Fixed builds may already have re-rendered the node away — that is a pass,
    // and the request count below is what actually decides it.
  });

  await expect(page.getByText(/rejected by engine/)).toHaveCount(0, { timeout: 30_000 });
  expect(count(), "select_map_node posts reaching the engine").toBe(1);
});

test("holding the play-best key does not dispatch a stale recommendation", async ({ page }) => {
  await openMapScreen(page);

  // `playBestChoice` is two round trips — ask the policy, then dispatch what it
  // said. Holding the key re-enters it, and the SECOND entry asks about a
  // screen the FIRST is already leaving, so its answer is stale by the time it
  // is sent. Holding the recommendation open reproduces that ordering exactly.
  await page.route("**/api/live/auto-action", async (route) => {
    await new Promise((r) => setTimeout(r, 1200));
    return route.continue();
  });
  let dispatched = 0;
  page.on("request", (r) => {
    if (r.url().includes("/api/live/action") && r.method() === "POST") dispatched += 1;
  });

  await page.keyboard.press("g");
  await page.waitForTimeout(300);
  await page.keyboard.press("g"); // auto-repeat, by hand

  // Wait for the press to actually land, then keep watching: the duplicate is a
  // LATER arrival, so asserting the moment the first one lands would pass on a
  // broken build too.
  await expect.poll(() => dispatched, { timeout: 30_000 }).toBeGreaterThan(0);
  await page.waitForTimeout(4000);

  await expect(page.getByText(/rejected by engine/)).toHaveCount(0);
  expect(dispatched, "actions dispatched by two play-best presses").toBe(1);
});
