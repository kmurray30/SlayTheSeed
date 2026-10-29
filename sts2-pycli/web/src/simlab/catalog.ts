/**
 * Client-side view of `/api/simlab/catalog`: lookups by id and the fuzzy
 * search the comboboxes run. Pure functions over the payload so they test
 * without a network.
 */
import type { Catalog, CatalogEncounter, CatalogMonster, CatalogNamed } from "./api";

export type CatalogKind = "card" | "relic" | "potion" | "power" | "monster";

export interface Option {
  id: string;
  name: string;
  /** Extra line shown dim next to the name (card type, act, …). */
  hint?: string;
}

export const EMPTY_CATALOG: Catalog = {
  characters: [],
  cards: [],
  relics: [],
  potions: [],
  powers: [],
  orbs: [],
  monsters: [],
  encounters: [],
  strategies: [],
};

export function optionsFor(cat: Catalog, kind: CatalogKind, character?: string): Option[] {
  switch (kind) {
    case "card": {
      // The active character's cards first (then colorless/curse/status, then
      // the rest) — a Silent player typing "str" wants Strike, not four of them.
      const own = (c: { character: string }) => (character && c.character === character ? 0 : c.character ? 2 : 1);
      return [...cat.cards]
        .sort((a, b) => own(a) - own(b) || a.name.localeCompare(b.name))
        .map((c) => ({ id: c.id, name: c.name, hint: [c.type, c.character].filter(Boolean).join(" · ") }));
    }
    case "relic":
      return cat.relics.map(named);
    case "potion":
      return cat.potions.map(named);
    case "power":
      return cat.powers.map(named);
    case "monster":
      return cat.monsters.map((m) => ({ id: m.id, name: m.name, hint: m.minHp !== undefined ? `${m.minHp}–${m.maxHp} HP` : undefined }));
  }
}

function named(x: CatalogNamed): Option {
  return { id: x.id, name: x.name, hint: x.rarity };
}

export function nameOf(cat: Catalog, kind: CatalogKind, id: string): string {
  const list: { id: string; name: string }[] =
    kind === "card" ? cat.cards : kind === "relic" ? cat.relics : kind === "potion" ? cat.potions : kind === "power" ? cat.powers : cat.monsters;
  const hit = list.find((x) => x.id === id);
  return hit ? hit.name : titleCase(id);
}

export function monsterOf(cat: Catalog, id: string): CatalogMonster | undefined {
  return cat.monsters.find((m) => m.id === id);
}

export function encounterOf(cat: Catalog, id: string | null): CatalogEncounter | undefined {
  return id ? cat.encounters.find((e) => e.id === id) : undefined;
}

/** Encounters grouped for a `<select>`: one `<optgroup>` per act table + tier. */
export function encounterGroups(cat: Catalog): { label: string; items: CatalogEncounter[] }[] {
  const groups = new Map<string, CatalogEncounter[]>();
  for (const e of cat.encounters) {
    const label = `Act ${e.act} · ${e.actName} · ${e.tier}`;
    groups.set(label, [...(groups.get(label) ?? []), e]);
  }
  return [...groups.entries()].map(([label, items]) => ({ label, items }));
}

export function titleCase(id: string): string {
  const snake = /_/.test(id) || id === id.toUpperCase() ? id : id.replace(/([a-z0-9])([A-Z])/g, "$1_$2");
  return snake
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

/**
 * Rank options for a query: id/name prefix matches first, then substring,
 * then a loose subsequence match ("dfnd" → Defend). Case-insensitive; the id's
 * underscores count as spaces so "demon form" finds DEMON_FORM_CARD.
 */
export function searchOptions(options: Option[], query: string, limit = 40): Option[] {
  const q = query.trim().toLowerCase();
  if (!q) return options.slice(0, limit);
  const scored: [number, Option][] = [];
  for (const o of options) {
    const id = o.id.toLowerCase().replace(/_/g, " ");
    const name = o.name.toLowerCase();
    let score = -1;
    if (name.startsWith(q) || id.startsWith(q)) score = 0;
    else if (name.includes(q) || id.includes(q)) score = 1;
    else if (subsequence(name, q) || subsequence(id, q)) score = 2;
    if (score >= 0) scored.push([score, o]);
  }
  scored.sort((a, b) => a[0] - b[0] || a[1].name.length - b[1].name.length || a[1].name.localeCompare(b[1].name));
  return scored.slice(0, limit).map(([, o]) => o);
}

function subsequence(text: string, q: string): boolean {
  let i = 0;
  for (const ch of text) {
    if (ch === q[i]) i += 1;
    if (i === q.length) return true;
  }
  return false;
}
