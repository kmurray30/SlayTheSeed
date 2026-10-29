import { test, expect } from "@playwright/test";

/**
 * The game-version selector in the browser-only build (GH #801): the wasm
 * engine offers both builds, a v0.111.0 run says so in the run bar, and the
 * build survives a reload (IndexedDB persist -> import). The golden parity
 * tests own "is the 0.111 state right"; this owns "does the page carry the
 * choice end to end".
 */
test("a v0.111.0 run is tagged and survives a reload", async ({ page }) => {
  await page.goto("/");
  const version = page.getByLabel("version");
  await expect(version.locator("option")).toHaveCount(2);
  await version.selectOption("v0.111.0");
  await page.getByLabel("seed", { exact: true }).fill("G0LDEN0001");
  await page.getByRole("button", { name: "Play yourself" }).click();
  await expect(page.locator(".gs-event-option").first()).toBeVisible({ timeout: 60_000 });

  const tag = page.getByTestId("run-game-version");
  await expect(tag).toContainText("v0.111.0");

  await page.reload();
  await expect(page.locator(".gs-event-option").first()).toBeVisible({ timeout: 60_000 });
  await expect(tag).toContainText("v0.111.0");
});

test("a default run carries no version tag", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("version").selectOption("v0.107.1");
  await page.getByLabel("seed", { exact: true }).fill("G0LDEN0001");
  await page.getByRole("button", { name: "Play yourself" }).click();
  await expect(page.locator(".gs-event-option").first()).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId("run-game-version")).toHaveCount(0);
});
