import { useEffect, useState } from "react";
import { api, type Meta, type SweepSpec } from "./api";
import NumberInput from "../components/NumberInput";
import { card } from "./styles";
import { formatDeck, parseDeckText, unknownCards } from "./deckText";

// Fallback lists, used only if GET /api/meta fails. Otherwise the form is
// driven by the backend's /api/meta payload.
const FALLBACK_ENCOUNTERS = [
  "SHRINKER_BEETLE_WEAK",
  "FUZZY_WURM_CRAWLER_WEAK",
  "MAWLER_NORMAL",
  "FLYCONID_NORMAL",
  "FOGMOG_NORMAL",
  "SLITHERING_STRANGLER_NORMAL",
  "SNAPPING_JAXFRUIT_NORMAL",
  "VINE_SHAMBLER_NORMAL",
  "BYGONE_EFFIGY_ELITE",
  "BYRDONIS_ELITE",
  "PHROG_PARASITE_ELITE",
  "VANTOM_BOSS",
];
const FALLBACK_STRATEGIES = [
  "random",
  "block_first",
  "attack_first",
  "min_overblock",
  "mcts",
];
const FALLBACK_DECKS: Record<string, string[]> = {
  ironclad_starter: [
    ...Array<string>(5).fill("STRIKE_IRONCLAD"),
    ...Array<string>(4).fill("DEFEND_IRONCLAD"),
    "BASH",
  ],
  // Names only (tooltips come from /api/meta when reachable) — keeps every
  // preset selectable even if the meta fetch fails.
  starter_upgraded: [],
  aggro_rush: [],
  turtle: [],
  scaling: [],
};
// Fallback card/monster lists are empty: client-side validation simply can't
// flag unknown cards until /api/meta loads (the server validates regardless).
const FALLBACK_CARDS: string[] = [];
const FALLBACK_MONSTERS: string[] = [];

// ---------------------------------------------------------------------------
// Deck variants
// ---------------------------------------------------------------------------
interface DeckVariant {
  /** Stable React key, independent of name/position (so removals don't remount). */
  id: string;
  name: string;
  text: string;
}

/**
 * Build a variant with a fresh stable id.
 *
 * Plain counter, NOT crypto.randomUUID(): that API only exists in secure
 * contexts, and the dev server is reached over plain http (http://a4000:5173),
 * where it is undefined and would blank the whole page on first render.
 */
let variantSeq = 0;
function makeVariant(name: string, text: string): DeckVariant {
  variantSeq += 1;
  return { id: `v${variantSeq}`, name, text };
}

/** Server caps a deck at 100 cards; mirror it client-side for instant feedback. */
const MAX_DECK_CARDS = 100;

/**
 * Validate a single variant against its peers and the known card list. Returns
 * a list of human-readable error strings (empty = valid).
 */
function variantErrors(
  variant: DeckVariant,
  index: number,
  all: DeckVariant[],
  knownCards: string[],
): string[] {
  const errs: string[] = [];
  const trimmed = variant.name.trim();
  if (trimmed === "") {
    errs.push("name required");
  } else if (trimmed.includes("/")) {
    errs.push('name may not contain "/"');
  } else if (all.some((d, i) => i !== index && d.name.trim() === trimmed)) {
    errs.push("duplicate name");
  }
  const parsed = parseDeckText(variant.text);
  errs.push(...parsed.errors);
  if (parsed.cards.length === 0) {
    errs.push("deck is empty");
  } else if (parsed.cards.length > MAX_DECK_CARDS) {
    errs.push(`deck too large (${parsed.cards.length}/${MAX_DECK_CARDS})`);
  }
  // Skip unknown-card checks until meta supplies the card list.
  if (knownCards.length > 0) {
    const unknown = unknownCards(parsed.cards, knownCards);
    if (unknown.length > 0) errs.push(`unknown card(s): ${unknown.join(", ")}`);
  }
  return errs;
}

/** Append a "-N" suffix to `base` that doesn't collide with `taken`. */
function uniqueName(base: string, taken: string[]): string {
  if (!taken.includes(base)) return base;
  // Strip an existing "-N" suffix so cloning "x-2" gives "x-3", not "x-2-2".
  const root = base.replace(/-\d+$/, "");
  for (let n = 2; ; n++) {
    const candidate = `${root}-${n}`;
    if (!taken.includes(candidate)) return candidate;
  }
}

/** Human label for a custom-enemy encounter entry ("monsters:Nibbit,Nibbit"). */
function customEnemyLabel(entry: string): string {
  const lineup = entry.slice("monsters:".length).split(",");
  const first = lineup[0] ?? entry;
  return lineup.length > 1 ? `${first} ×${lineup.length}` : first;
}

// ---------------------------------------------------------------------------
// SweepForm
// ---------------------------------------------------------------------------
export default function StudiesForm({ onSubmitted }: { onSubmitted: (id: string) => void }) {
  const [meta, setMeta] = useState<Meta>({
    deck_presets: FALLBACK_DECKS,
    strategies: FALLBACK_STRATEGIES,
    encounters: FALLBACK_ENCOUNTERS,
    cards: FALLBACK_CARDS,
    monsters: FALLBACK_MONSTERS,
  });
  const [name, setName] = useState("sweep");
  const [encounter, setEncounter] = useState(FALLBACK_ENCOUNTERS[2]);
  // Deck variants: each is an editable name + textarea. One per row. Initialized
  // from the ironclad_starter preset once meta loads.
  const [decks, setDecks] = useState<DeckVariant[]>([
    makeVariant("ironclad_starter", formatDeck(FALLBACK_DECKS.ironclad_starter ?? [])),
  ]);
  // Custom enemy lineups, e.g. "monsters:Nibbit,Nibbit", added alongside the
  // selected encounter id.
  const [customEncounters, setCustomEncounters] = useState<string[]>([]);
  const [monsterPick, setMonsterPick] = useState("");
  const [monsterCount, setMonsterCount] = useState(1);
  const [ascMin, setAscMin] = useState(0);
  const [ascMax, setAscMax] = useState(0);
  // Default: mcts only (the deployed run-AI combat agent); other strategies are
  // opt-in. `random` is a sanity floor.
  const [strategies, setStrategies] = useState<string[]>(["mcts"]);
  const [nSeeds, setNSeeds] = useState(100);
  const [mctsSims, setMctsSims] = useState(400);
  const [mctsDepth, setMctsDepth] = useState(200);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // Load meta on mount; keep the fallback lists if the fetch fails.
  useEffect(() => {
    let cancelled = false;
    api
      .meta()
      .then((m) => {
        if (cancelled) return;
        setMeta(m);
        // Only adopt a meta-driven default if the user hasn't already changed
        // the selection away from the initial fallback default.
        if (m.encounters.length > 0) {
          setEncounter((cur) =>
            cur === FALLBACK_ENCOUNTERS[2] ? (m.encounters[2] ?? m.encounters[0]) : cur,
          );
        }
        // Seed the first variant from a real preset (prefer ironclad_starter).
        const presetNames = Object.keys(m.deck_presets);
        const seedName = m.deck_presets.ironclad_starter
          ? "ironclad_starter"
          : presetNames[0];
        if (seedName) {
          setDecks((cur) =>
            // Only overwrite the untouched initial variant, never user edits.
            cur.length === 1 &&
            cur[0].name === "ironclad_starter" &&
            cur[0].text === formatDeck(FALLBACK_DECKS.ironclad_starter ?? [])
              ? [makeVariant(seedName, formatDeck(m.deck_presets[seedName]))]
              : cur,
          );
        }
        if (m.monsters.length > 0) {
          setMonsterPick((cur) => cur || m.monsters[0]);
        }
      })
      .catch(() => {
        // ignore: fall back to the hardcoded lists already in state
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const toggleStrategy = (s: string) =>
    setStrategies((cur) =>
      cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s],
    );

  // --- Deck variant handlers ---
  const updateVariant = (i: number, patch: Partial<DeckVariant>) =>
    setDecks((cur) => cur.map((d, j) => (j === i ? { ...d, ...patch } : d)));
  const removeVariant = (i: number) =>
    setDecks((cur) => (cur.length > 1 ? cur.filter((_, j) => j !== i) : cur));
  const addVariant = () =>
    setDecks((cur) => {
      const last = cur[cur.length - 1];
      const name = uniqueName(last.name.trim() || "variant", cur.map((d) => d.name.trim()));
      return [...cur, makeVariant(name, last.text)];
    });

  // Per-variant validation errors (index-aligned with `decks`).
  const deckErrors = decks.map((d, i) => variantErrors(d, i, decks, meta.cards));
  const hasDeckErrors = deckErrors.some((e) => e.length > 0);

  // --- Custom enemy handlers ---
  const addCustomEnemy = () => {
    if (!monsterPick) return;
    const n = Math.max(1, Math.min(4, Math.floor(monsterCount)));
    const entry = `monsters:${Array<string>(n).fill(monsterPick).join(",")}`;
    setCustomEncounters((cur) => (cur.includes(entry) ? cur : [...cur, entry]));
  };
  const removeCustomEnemy = (entry: string) =>
    setCustomEncounters((cur) => {
      const next = cur.filter((e) => e !== entry);
      // "none (custom only)" with no custom entries left would submit an
      // empty encounter list — fall back to the first real encounter.
      if (next.length === 0) {
        setEncounter((enc) => enc || meta.encounters[0] || "");
      }
      return next;
    });

  const submit = async () => {
    setErr(null);
    // Run all guards before building the spec or touching busy state.
    if (strategies.length === 0) {
      setErr("select at least one strategy");
      return;
    }
    if (nSeeds < 1) {
      setErr("seeds must be at least 1");
      return;
    }
    if (ascMin > ascMax) {
      setErr("ascension min must be ≤ max");
      return;
    }
    if (hasDeckErrors) {
      setErr("fix deck variant errors before running");
      return;
    }
    const ascensions: number[] = [];
    for (let a = ascMin; a <= ascMax; a++) ascensions.push(a);
    const deckPairs: [string, string[]][] = decks.map((d) => [
      d.name.trim(),
      parseDeckText(d.text).cards,
    ]);
    const spec: SweepSpec = {
      name,
      // "" = "none (custom only)" — sweep just the custom enemy lineups
      encounters: [...(encounter ? [encounter] : []), ...customEncounters],
      ascensions,
      strategies,
      n_seeds: nSeeds,
      decks: deckPairs,
      mcts_sims: mctsSims,
      mcts_depth: mctsDepth,
    };
    setBusy(true);
    try {
      const { sweep_id } = await api.submit(spec);
      onSubmitted(sweep_id);
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={card}>
      <h2 style={{ marginTop: 0 }}>New study</h2>
      <p style={{ marginTop: 0, color: "var(--muted)", fontSize: 13 }}>
        Deck variants × encounters × ascensions from turn 1 — the batch view. For one
        exact position, use the Sandbox tab.
      </p>
      <div style={{ display: "grid", gap: 12, gridTemplateColumns: "1fr 1fr" }}>
        <label>
          Name
          <br />
          <input value={name} onChange={(e) => setName(e.target.value)} style={{ width: "100%" }} />
        </label>
        <label>
          Encounter
          <br />
          <select
            value={encounter}
            onChange={(e) => setEncounter(e.target.value)}
            style={{ width: "100%" }}
          >
            {customEncounters.length > 0 && (
              <option value="">— none (custom enemies only) —</option>
            )}
            {meta.encounters.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
          <div style={{ marginTop: 6, fontSize: 12 }}>
            <span style={{ color: "var(--muted)" }}>custom enemies: </span>
            <select
              value={monsterPick}
              onChange={(e) => setMonsterPick(e.target.value)}
              aria-label="custom enemy"
              style={{ maxWidth: 160 }}
            >
              {meta.monsters.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>{" "}
            <NumberInput
              min={1}
              max={4}
              step={1}
              value={monsterCount}
              ariaLabel="custom enemy count"
              onChange={setMonsterCount}
              width={48}
            />{" "}
            <button type="button" onClick={addCustomEnemy} disabled={!monsterPick}>
              add
            </button>
            <div style={{ marginTop: 4, display: "flex", flexWrap: "wrap", gap: 4 }}>
              {customEncounters.map((entry) => (
                <span
                  key={entry}
                  title={entry}
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 4,
                    background: "var(--accent-weak)",
                    border: "1px solid var(--border)",
                    borderRadius: 10,
                    padding: "1px 8px",
                  }}
                >
                  {customEnemyLabel(entry)}
                  <button
                    type="button"
                    aria-label={`remove ${entry}`}
                    onClick={() => removeCustomEnemy(entry)}
                    style={{
                      border: "none",
                      background: "none",
                      cursor: "pointer",
                      color: "var(--muted)",
                      padding: 0,
                      fontSize: 12,
                    }}
                  >
                    ✕
                  </button>
                </span>
              ))}
            </div>
          </div>
        </label>
        <label
          title="Number of simulated fights per strategy. Seed i gives every strategy the identical shuffle, so strategies are compared on the same draws."
        >
          combats per strategy
          <br />
          <NumberInput min={1} value={nSeeds} onChange={setNSeeds} width="100%" />
        </label>
        <label>
          Ascension min
          <br />
          <NumberInput min={0} value={ascMin} onChange={setAscMin} width="100%" />
        </label>
        <label>
          Ascension max
          <br />
          <NumberInput min={0} value={ascMax} onChange={setAscMax} width="100%" />
        </label>
        <fieldset style={{ border: "1px solid #ddd", borderRadius: 4 }}>
          <legend>Strategies</legend>
          {meta.strategies.map((s) => (
            <label key={s} style={{ display: "block" }}>
              <input
                type="checkbox"
                checked={strategies.includes(s)}
                onChange={() => toggleStrategy(s)}
              />{" "}
              {s}
            </label>
          ))}
        </fieldset>
      </div>
      <fieldset style={{ border: "1px solid #ddd", borderRadius: 4, marginTop: 12 }}>
        <legend>Deck variants</legend>
        <div style={{ display: "grid", gap: 12 }}>
          {decks.map((d, i) => (
            <div
              key={d.id}
              style={{
                border: "1px solid #eee",
                borderRadius: 4,
                padding: 8,
                display: "grid",
                gap: 6,
              }}
            >
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <input
                  value={d.name}
                  aria-label={`variant ${i + 1} name`}
                  placeholder="variant name"
                  onChange={(e) => updateVariant(i, { name: e.target.value })}
                  style={{ flex: 1 }}
                />
                <select
                  value=""
                  aria-label={`variant ${i + 1} load preset`}
                  onChange={(e) => {
                    const preset = e.target.value;
                    if (preset) {
                      updateVariant(i, { text: formatDeck(meta.deck_presets[preset] ?? []) });
                    }
                  }}
                >
                  <option value="">load preset…</option>
                  {Object.keys(meta.deck_presets).map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  aria-label={`remove variant ${i + 1}`}
                  onClick={() => removeVariant(i)}
                  disabled={decks.length <= 1}
                  title={decks.length <= 1 ? "at least one variant required" : "remove variant"}
                >
                  ✕
                </button>
              </div>
              <textarea
                value={d.text}
                aria-label={`variant ${i + 1} cards`}
                onChange={(e) => updateVariant(i, { text: e.target.value })}
                rows={7}
                spellCheck={false}
                style={{ width: "100%", fontFamily: "monospace", fontSize: 12 }}
              />
              {deckErrors[i].length > 0 && (
                <div style={{ color: "var(--bad, #d66)", fontSize: 12 }}>
                  {deckErrors[i].join("; ")}
                </div>
              )}
            </div>
          ))}
        </div>
        <button type="button" onClick={addVariant} style={{ marginTop: 8 }}>
          + add variant
        </button>
      </fieldset>
      <details style={{ marginTop: 12 }}>
        <summary style={{ cursor: "pointer" }}>Advanced (search budgets)</summary>
        <div
          style={{
            display: "grid",
            gap: 12,
            gridTemplateColumns: "1fr 1fr",
            marginTop: 8,
          }}
        >
          <label
            title="Search iterations the MCTS player runs for every single card-play decision inside one combat. More = stronger + slower."
          >
            MCTS: sims per decision
            <br />
            <NumberInput min={1} value={mctsSims} onChange={setMctsSims} width="100%" />
          </label>
          <label
            title="Max actions a search playout may run before the position is scored. 200 = engine default."
          >
            MCTS: rollout depth
            <br />
            <NumberInput min={1} value={mctsDepth} onChange={setMctsDepth} width="100%" />
          </label>
        </div>
        <p style={{ margin: "8px 0 0", fontSize: 12, color: "var(--muted)" }}>
          seeds = how many fights; sims = how hard mcts thinks per move
        </p>
      </details>
      <div style={{ marginTop: 12 }}>
        <button onClick={submit} disabled={busy || hasDeckErrors}>
          {busy ? "submitting…" : "Run sweep"}
        </button>
        {err && <span style={{ color: "var(--bad, #d66)", marginLeft: 12 }}>{err}</span>}
      </div>
    </div>
  );
}
