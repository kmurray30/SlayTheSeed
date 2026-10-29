import { useEffect, useState } from "react";
import NumberInput from "../components/NumberInput";
import Combobox from "./Combobox";
import type { Catalog } from "./api";
import { encounterGroups, encounterOf, monsterOf, nameOf, optionsFor, titleCase } from "./catalog";
import {
  PILES,
  addCard,
  addMonster,
  formatPosition,
  groupCards,
  moveCard,
  normalizePosition,
  removeCard,
  removeMonster,
  setGroupCount,
  setMonster,
  setPile,
  setPlayer,
  totalCards,
  updateCard,
  type CardRow,
  type PileName,
  type Position,
  type PowerRow,
} from "./position";

export interface NewPositionRequest {
  character: string;
  encounter: string | null;
  ascension: number;
  seed: number;
  keepPlayer?: boolean;
}

/**
 * The left column: every field of a position, editable, in the order a player
 * thinks about a fight — setup, player, cards by pile, enemies — and the raw
 * JSON underneath for the people who would rather type.
 */
export default function PositionEditor({
  position,
  catalog,
  onChange,
  onNew,
  onImportText,
  busy,
}: {
  position: Position;
  catalog: Catalog;
  onChange: (next: Position) => void;
  /** Build a fresh fight from the engine (encounter / seed) and replace the position. */
  onNew: (req: NewPositionRequest) => void;
  /** Hand text the browser could not parse as a position to the server (mod paste, engine wire). */
  onImportText: (text: string) => void;
  busy?: boolean;
}) {
  const pos = position;
  const P = pos.player;
  const character = catalog.characters.find((c) => c.id === pos.character);
  const patchPlayer = (patch: Partial<typeof P>) => onChange(setPlayer(pos, patch));
  const randomSeed = () => Math.floor(Math.random() * 90_000_000) + 1;

  return (
    <div className="sl-editor">
      {/* ── SETUP ─────────────────────────────────────────────────────── */}
      <section className="sl-panel">
        <div className="sl-panel-head">
          <span className="sl-label">Setup</span>
          <span className="sl-head-right">
            <span className="sl-label">new position for</span>
            <select
              aria-label="new position for"
              value=""
              disabled={busy}
              onChange={(e) => {
                const v = e.target.value;
                if (!v) return;
                onNew({
                  character: pos.character,
                  encounter: v === "__current__" ? pos.encounter : v,
                  ascension: pos.ascension,
                  seed: pos.seed,
                });
              }}
            >
              <option value="">—</option>
              {pos.encounter && <option value="__current__">{encounterOf(catalog, pos.encounter)?.display ?? pos.encounter} (this encounter)</option>}
              {encounterGroups(catalog).map((g) => (
                <optgroup key={g.label} label={g.label}>
                  {g.items.map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.display}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </span>
        </div>
        <div className="sl-row">
          <label className="sl-field">
            <span>character</span>
            <select
              aria-label="character"
              value={pos.character}
              onChange={(e) => {
                const c = catalog.characters.find((x) => x.id === e.target.value);
                onChange({ ...pos, character: e.target.value, player: { ...P, orbSlots: c?.orbSlots ?? 0, orbs: [] } });
              }}
            >
              {(catalog.characters.length ? catalog.characters : [{ id: pos.character, name: pos.character }]).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.id}
                </option>
              ))}
            </select>
          </label>
          <label className="sl-field">
            <span>ascension</span>
            <NumberInput min={0} max={10} value={pos.ascension} onChange={(v) => onChange({ ...pos, ascension: v })} width={56} ariaLabel="ascension" />
          </label>
          <label className="sl-field">
            <span>turn</span>
            <NumberInput min={1} value={pos.turn} onChange={(v) => onChange({ ...pos, turn: v })} width={56} ariaLabel="turn" />
          </label>
          <label className="sl-field">
            <span>elite</span>
            <input type="checkbox" aria-label="elite" checked={pos.isElite} onChange={(e) => onChange({ ...pos, isElite: e.target.checked })} />
          </label>
        </div>
        <div className="sl-row">
          <label className="sl-field">
            <span>encounter</span>
            <select
              aria-label="encounter"
              value={pos.encounter ?? ""}
              onChange={(e) => onChange({ ...pos, encounter: e.target.value || null })}
            >
              <option value="">— custom lineup —</option>
              {pos.encounter && !encounterOf(catalog, pos.encounter) && <option value={pos.encounter}>{pos.encounter}</option>}
              {encounterGroups(catalog).map((g) => (
                <optgroup key={g.label} label={g.label}>
                  {g.items.map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.display}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
          <label className="sl-field">
            <span>seed</span>
            <NumberInput min={1} value={pos.seed} onChange={(v) => onChange({ ...pos, seed: v })} width={110} ariaLabel="seed" />
          </label>
          <button type="button" title="new random seed" onClick={() => onChange({ ...pos, seed: randomSeed() })}>
            ↻
          </button>
          <button
            type="button"
            className="sl-chip"
            disabled={busy}
            title="deal turn 1 of this encounter with this seed: starter deck, fresh hand, enemy HP rolls and openers"
            onClick={() => onNew({ character: pos.character, encounter: pos.encounter, ascension: pos.ascension, seed: pos.seed })}
          >
            starting hand
          </button>
          <button
            type="button"
            className="sl-chip"
            disabled={!pos.encounter}
            title="reset the enemies to this encounter's lineup (HP at the monster's max, openers rolled by the engine)"
            onClick={() => {
              const enc = encounterOf(catalog, pos.encounter);
              if (!enc) return;
              onChange({
                ...pos,
                monsters: enc.monsters.map((m) => {
                  const info = monsterOf(catalog, m);
                  const hp = info?.maxHp ?? info?.minHp ?? 1;
                  return { monster: m, hp, maxHp: hp, block: 0, powers: [], intent: null };
                }),
              });
            }}
          >
            enemies
          </button>
        </div>
      </section>

      {/* ── PLAYER ────────────────────────────────────────────────────── */}
      <section className="sl-panel">
        <div className="sl-panel-head">
          <span className="sl-label">Player</span>
          {character && <span className="sl-muted">{character.name}</span>}
        </div>
        <div className="sl-row">
          <Num label="hp" aria="player hp" value={P.hp} onChange={(v) => patchPlayer({ hp: v })} />
          <Num label="maxHp" aria="player maxHp" value={P.maxHp} min={1} onChange={(v) => patchPlayer({ maxHp: v, hp: Math.min(P.hp, v) })} />
          <Num label="block" aria="player block" value={P.block} onChange={(v) => patchPlayer({ block: v })} />
          <Num label="energy" aria="player energy" value={P.energy} onChange={(v) => patchPlayer({ energy: v })} />
          <Num label="max" aria="player max" value={P.maxEnergy} onChange={(v) => patchPlayer({ maxEnergy: v })} />
          <Num label="stars" aria="player stars" value={P.stars} onChange={(v) => patchPlayer({ stars: v })} />
          <Num label="gold" aria="player gold" value={P.gold} onChange={(v) => patchPlayer({ gold: v })} width={70} />
          <Num label="orb slots" aria="player orb slots" value={P.orbSlots} onChange={(v) => patchPlayer({ orbSlots: v, orbs: P.orbs.slice(0, v) })} />
        </div>
        {P.orbSlots > 0 && (
          <div className="sl-sub">
            <span className="sl-label">orbs</span>
            {P.orbs.map((o, i) => (
              <div className="sl-row" key={i}>
                <select
                  aria-label={`orb ${i + 1} type`}
                  value={o.type}
                  onChange={(e) => {
                    const base = catalog.orbs.find((x) => x.id === e.target.value);
                    patchPlayer({ orbs: P.orbs.map((x, j) => (j === i ? { type: e.target.value, passive: base?.passive, evoke: base?.evoke } : x)) });
                  }}
                >
                  {(catalog.orbs.length ? catalog.orbs : [{ id: o.type, name: o.type }]).map((k) => (
                    <option key={k.id} value={k.id}>
                      {k.name}
                    </option>
                  ))}
                </select>
                <Num label="passive" aria={`orb ${i + 1} passive`} value={o.passive ?? 0} onChange={(v) => patchPlayer({ orbs: P.orbs.map((x, j) => (j === i ? { ...x, passive: v } : x)) })} />
                <Num label="evoke" aria={`orb ${i + 1} evoke`} value={o.evoke ?? 0} onChange={(v) => patchPlayer({ orbs: P.orbs.map((x, j) => (j === i ? { ...x, evoke: v } : x)) })} />
                <button type="button" className="sl-x" aria-label={`remove orb ${i + 1}`} onClick={() => patchPlayer({ orbs: P.orbs.filter((_, j) => j !== i) })}>
                  ×
                </button>
              </div>
            ))}
            {P.orbs.length < P.orbSlots && (
              <button type="button" className="sl-chip" onClick={() => patchPlayer({ orbs: [...P.orbs, { type: "Lightning", passive: 3, evoke: 8 }] })}>
                + orb
              </button>
            )}
          </div>
        )}
        {pos.character === "Necrobinder" && (
          <div className="sl-sub">
            <span className="sl-label">Osty</span>
            <div className="sl-row">
              <label className="sl-field">
                <span>summoned</span>
                <input
                  type="checkbox"
                  aria-label="osty summoned"
                  checked={!!P.osty}
                  onChange={(e) => patchPlayer({ osty: e.target.checked ? { alive: true, hp: 6, maxHp: 6, block: 0 } : null })}
                />
              </label>
              {P.osty && (
                <>
                  <Num label="hp" aria="osty hp" value={P.osty.hp} onChange={(v) => patchPlayer({ osty: { ...P.osty!, hp: v } })} />
                  <Num label="maxHp" aria="osty maxHp" value={P.osty.maxHp} min={1} onChange={(v) => patchPlayer({ osty: { ...P.osty!, maxHp: v } })} />
                  <Num label="block" aria="osty block" value={P.osty.block} onChange={(v) => patchPlayer({ osty: { ...P.osty!, block: v } })} />
                </>
              )}
            </div>
          </div>
        )}

        <div className="sl-sub">
          <span className="sl-label">relics</span>
          {P.relics.map((r, i) => (
            <div className="sl-row sl-item" key={i}>
              <Combobox
                value={r.id}
                options={optionsFor(catalog, "relic")}
                ariaLabel={`relic ${i + 1}`}
                onChange={(id) => patchPlayer({ relics: P.relics.map((x, j) => (j === i ? { ...x, id } : x)) })}
              />
              <span className="sl-name">{nameOf(catalog, "relic", r.id)}</span>
              {(r.counter ?? 0) !== 0 && <span className="sl-muted">counter {r.counter}</span>}
              <button type="button" className="sl-x" aria-label={`remove relic ${i + 1}`} onClick={() => patchPlayer({ relics: P.relics.filter((_, j) => j !== i) })}>
                ×
              </button>
            </div>
          ))}
          <button type="button" className="sl-chip" onClick={() => patchPlayer({ relics: [...P.relics, { id: "", counter: 0 }] })}>
            + relic
          </button>
        </div>

        <div className="sl-sub">
          <span className="sl-label">potions</span>
          {P.potions.map((p, i) => (
            <div className="sl-row sl-item" key={i}>
              <Combobox
                value={p}
                options={optionsFor(catalog, "potion")}
                ariaLabel={`potion ${i + 1}`}
                onChange={(id) => patchPlayer({ potions: P.potions.map((x, j) => (j === i ? id : x)) })}
              />
              <span className="sl-name">{p ? nameOf(catalog, "potion", p) : "—"}</span>
              <button type="button" className="sl-x" aria-label={`remove potion ${i + 1}`} onClick={() => patchPlayer({ potions: P.potions.filter((_, j) => j !== i) })}>
                ×
              </button>
            </div>
          ))}
          <button type="button" className="sl-chip" onClick={() => patchPlayer({ potions: [...P.potions, ""] })}>
            + potion
          </button>
        </div>

        <PowerList
          label="powers"
          rows={P.powers}
          catalog={catalog}
          ariaPrefix="player power"
          onChange={(powers) => patchPlayer({ powers })}
        />
      </section>

      {/* ── CARDS ─────────────────────────────────────────────────────── */}
      <section className="sl-panel">
        <div className="sl-panel-head">
          <span className="sl-label">Cards</span>
          <span className="sl-label">{totalCards(pos)} in all four piles</span>
        </div>
        {PILES.map((pile) => (
          <PileEditor key={pile} pile={pile} pos={pos} catalog={catalog} onChange={onChange} />
        ))}
      </section>

      {/* ── ENEMIES ───────────────────────────────────────────────────── */}
      <section className="sl-panel">
        <div className="sl-panel-head">
          <span className="sl-label">Enemies</span>
          <button type="button" className="sl-chip" onClick={() => onChange(addMonster(pos, catalog.monsters[0]?.id ?? "Nibbit", catalog.monsters[0]?.maxHp ?? 10))}>
            + enemy
          </button>
        </div>
        {pos.monsters.length === 0 && <div className="sl-muted sl-empty">no enemies — add one, or pick an encounter and press “enemies”</div>}
        {pos.monsters.map((m, i) => {
          const info = monsterOf(catalog, m.monster);
          return (
            <div className="sl-enemy" key={i}>
              <div className="sl-row">
                <Combobox
                  value={m.monster}
                  options={optionsFor(catalog, "monster")}
                  ariaLabel={`enemy ${i + 1}`}
                  onChange={(id) => {
                    const next = monsterOf(catalog, id);
                    const hp = next?.maxHp ?? m.maxHp;
                    onChange(setMonster(pos, i, { monster: id, hp, maxHp: hp, intent: null }));
                  }}
                />
                <span className="sl-name">{nameOf(catalog, "monster", m.monster)}</span>
                <Num label="hp" aria={`enemy ${i + 1} hp`} value={m.hp} min={1} onChange={(v) => onChange(setMonster(pos, i, { hp: v, maxHp: Math.max(m.maxHp, v) }))} />
                <Num label="maxHp" aria={`enemy ${i + 1} maxHp`} value={m.maxHp} min={1} onChange={(v) => onChange(setMonster(pos, i, { maxHp: v, hp: Math.min(m.hp, v) }))} />
                <Num label="block" aria={`enemy ${i + 1} block`} value={m.block} onChange={(v) => onChange(setMonster(pos, i, { block: v }))} />
                <button type="button" className="sl-chip" aria-label={`remove enemy ${i + 1}`} onClick={() => onChange(removeMonster(pos, i))}>
                  remove enemy
                </button>
              </div>
              <div className="sl-row">
                <label className="sl-field">
                  <span>intent</span>
                  <select
                    aria-label={`enemy ${i + 1} intent`}
                    value={m.intent ?? ""}
                    onChange={(e) => onChange(setMonster(pos, i, { intent: e.target.value || null }))}
                  >
                    <option value="">— let the engine roll —</option>
                    {m.intent && !info?.moves.some((mv) => mv.id === m.intent) && <option value={m.intent}>{m.intent}</option>}
                    {(info?.moves ?? []).map((mv) => (
                      <option key={mv.id} value={mv.id}>
                        {mv.title}
                        {mv.intent ? ` · ${describeIntent(mv.intent)}` : ""}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <PowerList
                label="powers"
                rows={m.powers}
                catalog={catalog}
                ariaPrefix={`enemy ${i + 1} power`}
                onChange={(powers) => onChange(setMonster(pos, i, { powers }))}
              />
            </div>
          );
        })}
      </section>

      {/* ── POSITION JSON ─────────────────────────────────────────────── */}
      <JsonBox position={pos} onChange={onChange} onImportText={onImportText} />
    </div>
  );
}

function describeIntent(it: { kind: string; amount?: number; times?: number }): string {
  if (it.kind === "Attack") return `${it.amount ?? "?"}${it.times && it.times > 1 ? `×${it.times}` : ""}`;
  return it.kind + (it.amount !== undefined ? ` ${it.amount}` : "");
}

function Num({
  label,
  value,
  onChange,
  min = 0,
  width = 56,
  aria,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  min?: number;
  width?: number;
  /** Accessible name when the visible label repeats elsewhere on the page. */
  aria?: string;
}) {
  return (
    <label className="sl-field">
      <span>{label}</span>
      <NumberInput min={min} value={value} onChange={onChange} width={width} ariaLabel={aria ?? label} />
    </label>
  );
}

function PowerList({
  label,
  rows,
  catalog,
  ariaPrefix,
  onChange,
}: {
  label: string;
  rows: PowerRow[];
  catalog: Catalog;
  ariaPrefix: string;
  onChange: (rows: PowerRow[]) => void;
}) {
  return (
    <div className="sl-sub">
      <span className="sl-label">{label}</span>
      {rows.length === 0 && <span className="sl-muted sl-empty">none</span>}
      {rows.map((p, i) => (
        <div className="sl-row sl-item" key={i}>
          <Combobox
            value={p.id}
            options={optionsFor(catalog, "power")}
            ariaLabel={`${ariaPrefix} ${i + 1}`}
            onChange={(id) => onChange(rows.map((x, j) => (j === i ? { ...x, id } : x)))}
          />
          <span className="sl-name">{nameOf(catalog, "power", p.id)}</span>
          <Num label="amount" aria={`${ariaPrefix} ${i + 1} amount`} value={p.amount} min={-999} onChange={(v) => onChange(rows.map((x, j) => (j === i ? { ...x, amount: v } : x)))} />
          <button type="button" className="sl-x" aria-label={`remove ${ariaPrefix} ${i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            ×
          </button>
        </div>
      ))}
      <button type="button" className="sl-chip" onClick={() => onChange([...rows, { id: "", amount: 1 }])}>
        + power
      </button>
    </div>
  );
}

function PileEditor({
  pile,
  pos,
  catalog,
  onChange,
}: {
  pile: PileName;
  pos: Position;
  catalog: Catalog;
  onChange: (p: Position) => void;
}) {
  const rows = pos.piles[pile];
  const groups = groupCards(rows);
  const cardOptions = optionsFor(catalog, "card", pos.character);
  return (
    <div className="sl-pile">
      <div className="sl-pile-head">
        <span className="sl-label">{pile}</span>
        <span className="sl-muted">{rows.length}</span>
        <button type="button" className="sl-chip" aria-label={`add card to ${pile}`} onClick={() => onChange(addCard(pos, pile, ""))}>
          + card
        </button>
        {pile === "draw" && rows.length > 0 && <span className="sl-muted sl-note">top of the draw pile first — this order is what the engine deals</span>}
      </div>
      {rows.length === 0 && <div className="sl-muted sl-empty">empty</div>}
      {groups.map((g) => {
        const i = g.indices[0];
        const c = g.card;
        return (
          <div className="sl-row sl-item sl-card" key={`${pile}-${i}`}>
            <select
              aria-label={`${pile} card ${i + 1} pile`}
              value={pile}
              onChange={(e) => onChange(moveCard(pos, pile, i, e.target.value as PileName))}
            >
              {PILES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <Combobox
              value={c.card}
              options={cardOptions}
              ariaLabel={`${pile} card ${i + 1}`}
              onChange={(id) => onChange(setPile(pos, pile, rows.map((x, j) => (g.indices.includes(j) ? { ...x, card: id } : x))))}
            />
            <span className="sl-name">
              {c.card ? nameOf(catalog, "card", c.card) : "—"}
              {c.upgrade > 0 ? "+".repeat(Math.min(c.upgrade, 3)) : ""}
            </span>
            <span className="sl-muted">+</span>
            <NumberInput
              min={0}
              max={9}
              value={c.upgrade}
              width={44}
              ariaLabel={`${pile} card ${i + 1} upgrade`}
              onChange={(v) => onChange(setPile(pos, pile, rows.map((x, j) => (g.indices.includes(j) ? { ...x, upgrade: v } : x))))}
            />
            <span className="sl-muted">×</span>
            <NumberInput
              min={0}
              max={99}
              value={g.indices.length}
              width={44}
              ariaLabel={`${pile} card ${i + 1} count`}
              onChange={(v) => onChange(setPile(pos, pile, setGroupCount(rows, g, v)))}
            />
            <span className="sl-muted">cost</span>
            <input
              className="mono sl-cost"
              aria-label={`${pile} card ${i + 1} cost`}
              value={c.cost === null ? "" : String(c.cost)}
              placeholder="—"
              onChange={(e) => {
                const t = e.target.value.trim();
                const cost = t === "" ? null : Number.parseInt(t, 10);
                if (t !== "" && Number.isNaN(cost)) return;
                onChange(updateCard(pos, pile, i, { cost }));
                for (const j of g.indices.slice(1)) onChange(updateCard(pos, pile, j, { cost }));
              }}
            />
            <button
              type="button"
              className="sl-x"
              aria-label={`remove ${pile} card ${i + 1}`}
              onClick={() => {
                let next = pos;
                for (const j of [...g.indices].reverse()) next = removeCard(next, pile, j);
                onChange(next);
              }}
            >
              ×
            </button>
          </div>
        );
      })}
    </div>
  );
}

function JsonBox({
  position,
  onChange,
  onImportText,
}: {
  position: Position;
  onChange: (p: Position) => void;
  onImportText: (text: string) => void;
}) {
  const [text, setText] = useState(() => formatPosition(position));
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!dirty) setText(formatPosition(position));
  }, [position, dirty]);

  function apply() {
    const raw = text.trim();
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === "object" && ("piles" in parsed || parsed.schema)) {
        onChange(normalizePosition(parsed));
        setDirty(false);
        setError(null);
        return;
      }
    } catch {
      // not JSON we recognise — the server reads mod pastes and advisor logs
    }
    onImportText(raw);
    setDirty(false);
    setError(null);
  }

  return (
    <section className="sl-panel">
      <div className="sl-panel-head">
        <span className="sl-label">Position JSON</span>
        <span className="sl-head-right">
          <button type="button" className="sl-chip" onClick={apply} disabled={!dirty}>
            apply
          </button>
          <button
            type="button"
            className="sl-chip"
            disabled={!dirty}
            onClick={() => {
              setText(formatPosition(position));
              setDirty(false);
              setError(null);
            }}
          >
            revert
          </button>
        </span>
      </div>
      <div className="sl-muted sl-note">
        Paste a position here, or the state the game copied (SpireMentor <b>Copy state</b> / Ctrl+Shift+C) — then <b>apply</b>.
      </div>
      <textarea
        className="mono sl-json"
        aria-label="position json"
        spellCheck={false}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setDirty(true);
        }}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.key === "Enter") apply();
        }}
      />
      {error && <div className="sl-error">{error}</div>}
      <div className="sl-muted sl-note">{titleCase(position.character)} · A{position.ascension} · turn {position.turn}</div>
    </section>
  );
}

export type { CardRow };
