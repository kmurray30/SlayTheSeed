import { test, expect, type Page } from "@playwright/test";
import { menuItem } from "../e2e/topbarMenu";

/**
 * State import in the browser-only build: paste a position copied out of the
 * live game and rebuild it in the wasm engine. Two lanes since GH #708: the
 * RUN lane parks the paste on the map with its deck, relics, potions, gold and
 * HP grafted on; the COMBAT lane resumes the exact fight (hand, piles,
 * intents) on that run.
 *
 * This owns the page side end to end: the capability turns the button on, the
 * dialog previews the paste and offers the lanes the paste supports, and
 * importing replaces the run with the pasted one. Graft correctness ("is the
 * rebuilt state right") is the Rust suite's job (`game::tests::start_imported_*`,
 * `state_import_smoke`, the `state_import_combat_parity` golden).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/** A real SpireMentor fight capture (the combat-lane golden's first case). */
function combatPaste(): string {
  const golden = fileURLToPath(
    new URL("../../../sts2-rs/crates/sts2-workbench/tests/golden/state_import_combat.json", import.meta.url),
  );
  const doc = JSON.parse(readFileSync(golden, "utf8")) as { cases: { name: string; paste: string }[] };
  return doc.cases.find((c) => c.name === "single_enemy_ironclad")!.paste;
}

/** A map-less Ironclad run snapshot — the degraded run-lane path (no engine
 *  map to borrow), so it parks at the act entrance and says so. */
const PASTE = JSON.stringify({
  type: "advice_request",
  advice_for: "shop",
  seed: "IMP0RTSEED",
  act: 1,
  floor: 4,
  run_state: {
    character: "IRONCLAD",
    ascension: 0,
    hp: 42,
    max_hp: 80,
    gold: 175,
    potion_slot_count: 3,
    master_deck: [
      { id: "STRIKE_IRONCLAD" },
      { id: "STRIKE_IRONCLAD" },
      { id: "BASH", upgrade_level: 1 },
    ],
    relics: [{ id: "BURNING_BLOOD" }],
    potions: [{ slot: 0, id: "FIRE_POTION" }],
  },
});

function mapChoices(page: Page) {
  return page.getByRole("button", { name: /^Go to / });
}

async function startRun(page: Page, seed: string): Promise<void> {
  await page.goto("/");
  await expect(page.getByLabel("seed", { exact: true })).toBeVisible();
  await page.getByLabel("seed", { exact: true }).fill(seed);
  await page.getByRole("button", { name: "Play yourself" }).click();
  await expect(page.locator(".gs-event-option").first()).toBeVisible({ timeout: 60_000 });
}

test("the button shows, the dialog previews the paste, and importing rebuilds the run", async ({
  page,
}) => {
  // A different run is open first, to prove the import replaces it.
  await startRun(page, "OTHERSEED");

  // The capability turned the control on.
  const importBtn = await menuItem(page, "Run", "📋 Import state");
  await expect(importBtn).toBeVisible();
  await importBtn.click();

  const dialog = page.getByRole("dialog", { name: "Import state" });
  await expect(dialog).toBeVisible();
  await dialog.locator("textarea").fill(PASTE);

  // The preview names the run before anything is replaced. A shop paste
  // carries no fight, so only the run lane is offered (no combat radio, no
  // SimLab handoff).
  await expect(dialog.getByText(/Ironclad/)).toBeVisible({ timeout: 20_000 });
  await expect(dialog.getByText("IMP0RTSEED", { exact: true })).toBeVisible();
  await expect(dialog.getByText(/resume the fight/)).toHaveCount(0);
  await expect(dialog.getByRole("button", { name: /open in SimLab/ })).toHaveCount(0);

  await dialog.getByRole("button", { name: /^import \(replaces/ }).click();

  // The run was rebuilt: the dialog closes and the board lands on the map with
  // real moves to make.
  await expect(dialog).toBeHidden({ timeout: 30_000 });
  await expect.poll(() => mapChoices(page).count(), { timeout: 30_000 }).toBeGreaterThan(0);

  // The grafted HP rode across (42/80), shown on the player panel.
  await expect(page.getByText(/42\s*\/\s*80/).first()).toBeVisible();

  // A map-less paste says what it could not reproduce.
  await expect(page.getByText(/not everything carried over/)).toBeVisible();
  await expect(page.getByText(/act entrance/).first()).toBeVisible();
});

test("without a run open, an imported paste still rebuilds", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByLabel("seed", { exact: true })).toBeVisible();
  // The button is available on the start screen too.
  const importBtn = await menuItem(page, "Run", "📋 Import state");
  await expect(importBtn).toBeVisible();
  await importBtn.click();

  const dialog = page.getByRole("dialog", { name: "Import state" });
  await dialog.locator("textarea").fill(PASTE);
  await expect(dialog.getByText(/Ironclad/)).toBeVisible({ timeout: 20_000 });
  await dialog.getByRole("button", { name: /^import \(replaces/ }).click();

  await expect(dialog).toBeHidden({ timeout: 30_000 });
  await expect.poll(() => mapChoices(page).count(), { timeout: 30_000 }).toBeGreaterThan(0);
});

/**
 * The combat lane (GH #708): a fight paste offers "resume the fight", and
 * importing it lands on the exact board — the capture's hand, energy and the
 * Shrinker Beetle at full HP — playable in the wasm. The SimLab handoff is
 * offered too (the adapter runs in the worker).
 */
test("a fight paste resumes the exact fight in the browser", async ({ page }) => {
  await startRun(page, "OTHERSEED");
  const importBtn = await menuItem(page, "Run", "📋 Import state");
  await importBtn.click();
  const dialog = page.getByRole("dialog", { name: "Import state" });
  await dialog.locator("textarea").fill(combatPaste());

  await expect(dialog.getByText(/Ironclad/)).toBeVisible({ timeout: 20_000 });
  const combatLane = dialog.getByLabel(/resume the fight/);
  await expect(combatLane).toBeVisible();
  await expect(combatLane).toBeChecked();
  await expect(dialog.getByRole("button", { name: /open in SimLab/ })).toBeVisible();
  await expect(dialog.getByText(/combat board \(hand, piles, powers, intents\)/)).toBeVisible();

  await dialog.getByRole("button", { name: /^import \(replaces/ }).click();
  await expect(dialog).toBeHidden({ timeout: 30_000 });

  // The fight, not the map: the capture's enemy and hand are on the board.
  await expect(page.getByText("Shrinker Beetle").first()).toBeVisible({ timeout: 30_000 });
  await expect(page.locator("div.gs-card").first()).toBeVisible();
  await expect(page.getByText(/40\s*\/\s*40/).first()).toBeVisible();
  // The fight can be played: ending the turn advances it.
  await page.getByRole("button", { name: /end turn/i }).click();
  await expect(page.getByText("Shrinker Beetle").first()).toBeVisible();
});
