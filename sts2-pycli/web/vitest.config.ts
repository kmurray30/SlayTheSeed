import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // `e2e/` and `e2e-static/` hold Playwright specs (`npm run e2e`, `just
  // e2e-static`); exclude them from vitest, which would otherwise pick up
  // `*.spec.ts` and choke on Playwright's
  // `test.describe`. `node_modules`/`dist` are vitest defaults, restated because
  // supplying `exclude` overrides them.
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    globals: true,
    exclude: ["**/node_modules/**", "**/dist/**", "e2e/**", "e2e-static/**"],
  },
});
