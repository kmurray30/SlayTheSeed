// Page half of the Chrome harness (see chrome_bench.html, chrome_driver.mjs).
// A .js name, not .mjs: python -m http.server must send a JavaScript MIME type.
import init, { Workbench, version } from "./pkg/sts2_workbench_wasm.js";

const q = new URLSearchParams(location.search);
const seed = q.get("seed") ?? "BENCH1";
const reps = Math.max(1, Number(q.get("reps") ?? "7"));
const SIMS = [200, 800, 1000, 2000, 4000];
window.__lines = [];
const out = (s) => window.__lines.push(s);

try {
  await init(); // fetches ./pkg/sts2_workbench_wasm_bg.wasm relative to the glue
  const wb = new Workbench(JSON.stringify({ assets_base: "/assets" }));
  const api = (method, route, body) => {
    const r = JSON.parse(wb.call(method, route, body === undefined ? "" : JSON.stringify(body)));
    if (r.status !== 200) throw new Error(`${method} ${route} -> ${r.status} ${JSON.stringify(r.body)}`);
    return r.body;
  };
  const next = (step) => {
    const st = step.state ?? {};
    if (step.decision === "card_select") {
      const min = Math.max(1, st.min_select ?? 1);
      return ["select_cards", { indices: [...Array(min).keys()].join(",") }];
    }
    const o = (st.options ?? []).find((o) => o.enabled ?? true);
    return o ? [o.action ?? "proceed", o.args] : ["proceed", {}];
  };
  const fp = (step) => {
    const st = step.state ?? {};
    const enemies = (st.enemies ?? []).map((e) => `${e.name ?? "?"}:${JSON.stringify(e.hp)}`);
    const hand = (st.hand ?? []).map((c) => c.id ?? "?");
    return `enemies=${enemies.join(",")};hand=${hand.join(",")}`;
  };
  let step = api("POST", "/api/live/start", { character: "Ironclad", seed, ascension: 0, act1: "overgrowth" });
  const route = [];
  while (step.decision !== "combat_play") {
    if (route.length >= 60) throw new Error(`no fight reached: ${step.decision}`);
    const [action, args] = next(step);
    route.push([action, args]);
    step = api("POST", "/api/live/action", { action, args }).step;
  }
  out(`host=chrome-main ua=${navigator.userAgent} engine=${version()} seed=${seed} route=${JSON.stringify(route)}`);
  out(`position ${fp(step)}`);
  api("POST", "/api/bench", { sims: 4000, seed: 7 }); // global warm-up (tier-up), as in hint_bench.rs
  for (const sims of SIMS) {
    const body = { sims, seed: 7 };
    let t = performance.now();
    const first = api("POST", "/api/bench", body);
    const cold = performance.now() - t;
    const ms = [];
    for (let i = 0; i < reps; i++) {
      t = performance.now();
      api("POST", "/api/bench", body);
      ms.push(performance.now() - t);
    }
    ms.sort((a, b) => a - b);
    const med = ms[Math.floor(ms.length / 2)];
    const pv0 = JSON.stringify((first.pv ?? [])[0] ?? null);
    out(`sims=${sims} cold_ms=${cold.toFixed(2)} median_ms=${med.toFixed(2)} min_ms=${ms[0].toFixed(2)} ms_per_sim=${(med / sims).toFixed(5)} pv0=${pv0}`);
  }
} catch (e) {
  window.__error = String((e && e.stack) || e);
}
window.__done = true;
