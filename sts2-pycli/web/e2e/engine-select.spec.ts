import { test, expect } from "@playwright/test";

/**
 * The engine dropdown, and what happens when a start is refused.
 *
 * The deployed workbench image is deliberately C#-free (root `Dockerfile`
 * stage 3: no .NET runtime, no game DLLs). The dropdown offered "C#" anyway,
 * so picking it there spawned `dotnet`, which does not exist — `/api/live/start`
 * 500'd, the rejected promise was never caught, and "Play yourself" appeared to do
 * absolutely nothing.
 *
 * Both halves of the fix are asserted here, and both routes are stubbed so the
 * result does not depend on whether THIS machine happens to have the C# oracle
 * built.
 */

test("the dropdown offers only the engines the server can spawn", async ({ page }) => {
  await page.route("**/api/capabilities", (route) =>
    route.fulfill({ json: { backends: ["rust"] } }),
  );
  await page.goto("/");
  const engine = page.getByLabel("engine");
  await expect(engine).toBeVisible();
  await expect(engine.locator("option")).toHaveText(["Rust"]);
});

test("a C#-capable server still offers C#", async ({ page }) => {
  await page.route("**/api/capabilities", (route) =>
    route.fulfill({ json: { backends: ["rust", "csharp"] } }),
  );
  await page.goto("/");
  const engine = page.getByLabel("engine");
  await expect(engine.locator("option")).toHaveText(["Rust", "C#"]);
});

test("a refused start says why instead of doing nothing", async ({ page }) => {
  await page.route("**/api/capabilities", (route) =>
    route.fulfill({ json: { backends: ["rust", "csharp"] } }),
  );
  await page.route("**/api/live/start", (route) =>
    route.fulfill({
      status: 400,
      json: { detail: "could not start the 'csharp' engine: EOF from game process." },
    }),
  );
  await page.goto("/");
  await page.getByLabel("engine").selectOption("csharp");
  await page.getByRole("button", { name: "Play yourself" }).click();
  // The whole point of the bug report: the click must produce a visible answer.
  await expect(page.getByText(/start failed/i).first()).toBeVisible();
  await expect(page.getByText(/EOF from game process/).first()).toBeVisible();
});
