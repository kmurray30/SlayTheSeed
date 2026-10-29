// Drives chrome_bench.html in headless Chromium via Playwright and prints its
// lines (same format as hint_bench.rs). PW_ROOT = a directory whose
// node_modules has `playwright` (e.g. sts2-pycli/web after `npm ci`).
//
//   PW_ROOT=.../sts2-pycli/web node chrome_driver.mjs http://127.0.0.1:8791/chrome_bench.html BENCH1 7
import { createRequire } from "node:module";

const require = createRequire(`${process.env.PW_ROOT ?? process.cwd()}/package.json`);
const { chromium } = require("playwright");

const [url, seed = "BENCH1", reps = "7"] = process.argv.slice(2);
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage();
  page.on("console", (m) => { if (m.type() === "error") console.error(`[page] ${m.text()}`); });
  await page.goto(`${url}?seed=${encodeURIComponent(seed)}&reps=${reps}`);
  await page.waitForFunction(() => window.__done === true, null, { timeout: 900_000 });
  const err = await page.evaluate(() => window.__error);
  if (err) {
    console.error(err);
    process.exitCode = 1;
  } else {
    console.log(`browser=${browser.version()}`);
    console.log((await page.evaluate(() => window.__lines)).join("\n"));
  }
} finally {
  await browser.close();
}
