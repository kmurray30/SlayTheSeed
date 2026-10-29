import { defineConfig, devices } from "@playwright/test";

// End-to-end tests drive the real workbench: the Vite dev server (proxying
// /api to the FastAPI backend) + the in-process Rust engine. They are slow and
// stateful (one shared backend), so run serially with generous timeouts.
//
// Launch both servers via `webServer`. The backend reuses the already-built
// `sts2_rs` wheel in `.venv` (no maturin rebuild), reload off for stability.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    headless: true,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "PYTHONPATH=python STS2_RELOAD=0 .venv/bin/python -m workbench",
      cwd: "..",
      url: "http://127.0.0.1:8077/api/health",
      timeout: 180_000,
      reuseExistingServer: true,
      env: { STS2_HOST: "127.0.0.1", STS2_PORT: "8077" },
    },
    {
      command: "npm run dev",
      url: "http://localhost:5173",
      timeout: 60_000,
      reuseExistingServer: true,
    },
  ],
});
