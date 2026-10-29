import type { Locator, Page } from "@playwright/test";

/** The top bar's Run / Analyze / Tools dropdowns (src/topbar/TopBar.tsx).
 *  Opens the named menu and returns the item, so a spec reads
 *  `await menuItem(page, "Run", "End run").click()`. The menu stays open
 *  until the item is picked (or Escape), so `expect(...).toBeVisible()` on
 *  the returned locator works as a presence check too. */
export async function openMenu(page: Page, menu: "Run" | "Analyze" | "Tools"): Promise<Locator> {
  const trigger = page.getByRole("button", { name: new RegExp(`^${menu}\\b`) });
  if ((await trigger.getAttribute("aria-expanded")) !== "true") await trigger.click();
  return page.getByRole("menu");
}

export async function menuItem(page: Page, menu: "Run" | "Analyze" | "Tools", name: string | RegExp): Promise<Locator> {
  const list = await openMenu(page, menu);
  return list.getByRole("menuitem", { name });
}

export async function closeMenu(page: Page): Promise<void> {
  await page.keyboard.press("Escape");
}
