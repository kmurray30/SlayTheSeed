// GH #709 transport spike (the browser half; run it through
// ../../scripts/spike_bridge_transport.py, which starts the two fake control
// planes and passes their ports in $PORTS): from the real
// https://your-workbench.example.com page, fetch the advisor control plane on
// loopback in Chromium and Firefox. Two planes: one with the page origin
// allow-listed (CORS + Private Network Access headers), one with none (the
// server before the opt-in). $FIREFOX_PATH overrides the Firefox binary.
// Findings: docs/browser-workbench.md, "Live-game bridge".
import { chromium, firefox } from "@playwright/test";
const PAGE = process.env.PAGE_URL || "https://your-workbench.example.com/";
const ports = JSON.parse(process.env.PORTS);
const hosts = ["127.0.0.1", "localhost"];
async function probe(page, base, path, init) {
  return page.evaluate(async ([base, path, init]) => {
    const t0 = performance.now();
    try {
      const r = await fetch(base + path, init);
      const body = await r.text();
      return { ok: r.ok, status: r.status, body: body.slice(0, 80), ms: Math.round(performance.now() - t0) };
    } catch (e) {
      return { ok: false, error: String(e && e.message || e), ms: Math.round(performance.now() - t0) };
    }
  }, [base, path, init]);
}
const out = {};
const FF = process.env.FIREFOX_PATH;
for (const [name, launcher, opts] of [["chromium", chromium, {}], ["firefox", firefox, FF ? { executablePath: FF } : {}]]) {
  let browser;
  try { browser = await launcher.launch({ headless: true, ...opts }); }
  catch (e) { out[name] = { launch_error: String(e.message).split("\n")[0] }; continue; }
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const console_ = [];
  page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning") console_.push(m.text().slice(0, 200)); });
  for (let attempt = 0; ; attempt++) {
    try { await page.goto(PAGE, { waitUntil: "domcontentloaded", timeout: 60000 }); break; }
    catch (e) { if (attempt >= 4) throw e; await new Promise((r) => setTimeout(r, 2000)); }
  }
  const res = { version: browser.version(), origin: await page.evaluate(() => location.origin), probes: {} };
  for (const [label, port] of Object.entries(ports)) {
    for (const host of hosts) {
      const base = `http://${host}:${port}`;
      res.probes[`${label} ${base} GET /status`] = await probe(page, base, "/status", {});
      res.probes[`${label} ${base} POST /restore`] = await probe(page, base, "/restore", {
        method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ snapshot_id: "111-1" }),
      });
    }
  }
  res.console = console_.slice(0, 12);
  out[name] = res;
  await browser.close();
}
console.log(JSON.stringify(out, null, 1));
