/**
 * Clean up stray template artifacts from game localization strings.
 * The game uses a template system like `{var turns}` and `{? conditional|alternative}`.
 * Some of these aren't fully resolved during extraction.
 */
export function cleanDescription(text: string): string {
  if (!text) return '';
  let s = text;

  // FIRST: Convert {TemplateVar} placeholders BEFORE any } stripping
  // These may be inside HTML spans like <span class="desc-gold">{Card1}</span>
  // Need to handle both bare {Var} and HTML-wrapped {Var}
  s = s.replace(/\{([A-Za-z][A-Za-z0-9]*)\}/g, (_, name) => {
    const readable = name
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
    return `<span class="desc-gold">${readable}</span>`;
  });

  // Handle {Var:plural:singular|plural} syntax
  s = s.replace(/\{(\w+):plural:(\w+)\|(\w+)\}/g, (_, _name, _singular, plural) => {
    return `<span class="desc-gold">${plural}</span>`;
  });

  // Clean ?|hint text} patterns (partially-parsed placeholders)
  s = s.replace(/\?\|[^}]*\}/g, '?');

  // Remove empty conditionals: ?|} or ?\|} (with optional HTML between)
  s = s.replace(/\?\s*\|?\s*\}/g, '');

  // Clean "? word|? words}" plural patterns
  s = s.replace(/\?\s*\w+\s*\|\s*\?\s*\w+\s*\}/g, '?');

  // Clean "? word)" and "? word)|}" patterns
  s = s.replace(/\?\s*(\w+)\s*\)\s*\|?\s*\}/g, '? $1');

  // Clean "word}" where word is preceded by ? (with possible HTML in between)
  // Handle "? turns}", "? cards}", "damage? times}" etc.
  s = s.replace(/(\?\s*(?:<[^>]+>\s*)*)(\w+)\s*\}/g, '$1$2');

  // Clean word} patterns preceded by a space (like "sacrifices}")
  s = s.replace(/\s(\w+)\}/g, ' $1');

  // Strip standalone stray } that aren't part of JS/HTML
  // Only remove } that appear after word characters with no { before
  s = s.replace(/(\w)\}/g, '$1');

  // Convert remaining BBCode tags to readable text
  s = s.replace(/\[star\]/gi, '\u2605');
  s = s.replace(/\[energy\]/gi, '\u26A1');

  // Clean up stray [/color] BBCode closing tags that got through
  s = s.replace(/\[\/\w+\]/g, '');

  // Clean up stray opening BBCode tags that weren't matched
  s = s.replace(/\[\w+[^\]]*\]/g, '');

  // Collapse nested spans where outer span is redundant
  // e.g., <span class="desc-red"><span class="desc-gold">X</span></span> → <span class="desc-gold">X</span>
  s = s.replace(/<span class="desc-\w+">\s*(<span class="desc-\w+">[^<]*<\/span>)\s*<\/span>/g, '$1');

  // Clean up double spaces
  s = s.replace(/  +/g, ' ');

  // Clean up empty parentheses left behind
  s = s.replace(/\(\s*\)/g, '');

  return s.trim();
}

/**
 * Process BBCode tags in text to HTML spans.
 */
export function processBBCode(text: string): string {
  if (!text) return '';
  return text
    .replace(/\[gold\](.*?)\[\/gold\]/gi, '<span class="desc-gold">$1</span>')
    .replace(/\[red\](.*?)\[\/red\]/gi, '<span class="desc-red">$1</span>')
    .replace(/\[green\](.*?)\[\/green\]/gi, '<span class="desc-green">$1</span>')
    .replace(/\[blue\](.*?)\[\/blue\]/gi, '<span class="desc-blue">$1</span>')
    .replace(/\[purple\](.*?)\[\/purple\]/gi, '<span class="desc-purple">$1</span>')
    .replace(/\[orange\](.*?)\[\/orange\]/gi, '<span class="desc-orange">$1</span>')
    .replace(/\[aqua\](.*?)\[\/aqua\]/gi, '<span class="desc-aqua">$1</span>')
    .replace(/\[pink\](.*?)\[\/pink\]/gi, '<span class="desc-pink">$1</span>')
    .replace(/\[rainbow[^\]]*\](.*?)\[\/rainbow\]/gi, '<span class="desc-gold">$1</span>')
    .replace(/\[star\]/gi, '\u2605')
    .replace(/\[energy\]/gi, '\u26A1');
}

/**
 * Get CSS class for character name.
 */
export function charClass(name: string): string {
  const n = name.toLowerCase();
  if (['ironclad', 'silent', 'defect', 'necrobinder', 'regent'].includes(n)) return `char-${n}`;
  return '';
}

// ─── Monster move description generation ───

interface MoveIntent {
  type: string;
  damage?: number;
  damage_base?: number;
  hits?: number;
  hits_base?: number;
  amount?: number;
  amount_base?: number;
  /** Ascension level threshold at which the harder values kick in. */
  ascension?: number;
}

interface Move {
  id: string;
  title: string;
  intents: MoveIntent[];
  effects: string[];
}

interface MoveDescription {
  /** CSS class for the intent type (intent-attack, intent-block, etc.) */
  intentClass: string;
  /** Short label for the intent tag pill */
  intentLabel: string;
  /** Ascension threshold callout, e.g. "12 below A9" */
  ascensionTitle?: string;
}

/**
 * Format a numeric value with its base (lower-difficulty) variant when present.
 * `7` → `"7"`; `12 / 13` → `"12/13"`; `13 / 13` → `"13"`.
 */
function formatScaled(asc: number | undefined, base: number | undefined): string {
  if (asc == null) return '';
  if (base == null || base === asc) return String(asc);
  return `${base}/${asc}`;
}

/**
 * Generate a consistent description for a single intent.
 */
/**
 * Map monster class name to the image filename (without extension).
 * Many monsters share base skeletons or have different animation names.
 */
const MONSTER_IMAGE_ALIASES: Record<string, string> = {
  // Aeonglass has no rig in v0.107.1 — the game renders it as a static
  // hourglass placeholder (scenes/creature_visuals/aeonglass.tscn).
  Aeonglass: 'hourglass_placeholder',
  TorchHeadAmalgam: 'amalgam',
  GlobeHead: 'orb_head',
  Flyconid: 'flying_mushrooms',
  DecimillipedeSegment: 'decimillipede',
  LivingFog: 'living_smog',
  SkulkingColony: 'living_shield',
  // Crusher (left arm) and Rocket (right arm) are the two targetable parts of
  // the Kaiser Crab boss — Crusher.cs/Rocket.cs drive the kaiser_crab_left_* /
  // kaiser_crab_right_* SFX and share its sprite. They are NOT the (unused)
  // infested_guardian rig, which belongs to no monster class at all.
  Crusher: 'kaiser_crab',
  Rocket: 'kaiser_crab',
  Ovicopter: 'egg_layer',
  BowlbugEgg: 'bowlbug',
  BowlbugNectar: 'bowlbug',
  BowlbugRock: 'bowlbug',
  BowlbugSilk: 'bowlbug',
  CalcifiedCultist: 'cultists',
  DampCultist: 'cultists',
  BattleFriendV1: 'battleworn_dummy',
  BattleFriendV2: 'battleworn_dummy',
  BattleFriendV3: 'battleworn_dummy',
  BigDummy: 'battleworn_dummy',
};

export function monsterImageName(className: string): string {
  if (MONSTER_IMAGE_ALIASES[className]) return MONSTER_IMAGE_ALIASES[className];
  // Default: convert PascalCase to snake_case
  return className.replace(/([A-Z])/g, (m: string, p1: string, offset: number) =>
    (offset > 0 ? '_' : '') + p1
  ).toLowerCase();
}

/** Map intent type to icon filename */
export function intentIconFile(type: string): string {
  switch (type) {
    case 'attack':
    case 'SingleAttackIntent':
    case 'multi_attack':
    case 'death_blow':
      return 'attack/intent_attack_1.png';
    case 'block':
      return 'intent_defend.png';
    case 'buff':
      return 'intent_buff.png';
    case 'debuff':
      return 'intent_debuff.png';
    case 'stun':
      return 'intent_stun.png';
    case 'sleep':
      return 'intent_sleep.png';
    case 'summon':
      return 'intent_summon.png';
    case 'heal':
      return 'intent_heal.png';
    case 'escape':
      return 'intent_escape.png';
    case 'status':
      return 'intent_status_card.png';
    case 'hidden':
      return 'intent_hidden.png';
    default:
      return 'intent_unknown.png';
  }
}

function ascensionTitle(intent: MoveIntent): string | undefined {
  if (intent.ascension == null) return undefined;
  const parts: string[] = [];
  if (intent.damage_base != null && intent.damage != null) {
    parts.push(`${intent.damage_base} damage below A${intent.ascension}, ${intent.damage} at A${intent.ascension}+`);
  }
  if (intent.hits_base != null && intent.hits != null) {
    parts.push(`${intent.hits_base} hits below A${intent.ascension}, ${intent.hits} at A${intent.ascension}+`);
  }
  if (intent.amount_base != null && intent.amount != null) {
    parts.push(`${intent.amount_base} below A${intent.ascension}, ${intent.amount} at A${intent.ascension}+`);
  }
  return parts.length > 0 ? parts.join('; ') : undefined;
}

function describeIntent(intent: MoveIntent): MoveDescription {
  const title = ascensionTitle(intent);
  switch (intent.type) {
    case 'attack':
    case 'SingleAttackIntent': {
      const dmg = formatScaled(intent.damage, intent.damage_base);
      return {
        intentClass: 'intent-attack',
        intentLabel: dmg || 'ATK',
        ascensionTitle: title,
      };
    }
    case 'multi_attack': {
      const dmg = formatScaled(intent.damage, intent.damage_base);
      const hits = formatScaled(intent.hits, intent.hits_base);
      if (dmg && hits) {
        return { intentClass: 'intent-attack', intentLabel: `${dmg}x${hits}`, ascensionTitle: title };
      } else if (dmg) {
        return { intentClass: 'intent-attack', intentLabel: `${dmg}xN`, ascensionTitle: title };
      }
      return { intentClass: 'intent-attack', intentLabel: 'Multi', ascensionTitle: title };
    }
    case 'block': {
      const amt = formatScaled(intent.amount, intent.amount_base);
      return {
        intentClass: 'intent-block',
        intentLabel: amt ? `Block ${amt}` : 'Block',
        ascensionTitle: title,
      };
    }
    case 'buff':
      return { intentClass: 'intent-buff', intentLabel: 'Buff' };
    case 'debuff':
      return { intentClass: 'intent-debuff', intentLabel: 'Debuff' };
    case 'stun':
      return { intentClass: 'intent-stun', intentLabel: 'Stun' };
    case 'sleep':
      return { intentClass: 'intent-sleep', intentLabel: 'Sleep' };
    case 'summon':
      return { intentClass: 'intent-summon', intentLabel: 'Summon' };
    case 'heal':
      return { intentClass: 'intent-heal', intentLabel: 'Heal' };
    case 'escape':
      return { intentClass: 'intent-escape', intentLabel: 'Escape' };
    case 'status':
      return { intentClass: 'intent-status', intentLabel: 'Status' };
    case 'hidden':
      return { intentClass: 'intent-escape', intentLabel: '???' };
    case 'death_blow':
      return { intentClass: 'intent-attack', intentLabel: 'Death' };
    default:
      return { intentClass: '', intentLabel: intent.type };
  }
}

/**
 * Build an array of intent tag descriptions for a move.
 */
export function getMoveIntents(move: Move): MoveDescription[] {
  return move.intents.map(describeIntent);
}

/**
 * Build a textual description of what a move does from structured data.
 * Uses effects when available (they have specific buff/debuff names),
 * falls back to intent-derived descriptions when effects is empty.
 */
export function getMoveEffectLines(move: Move, powerSlugs: Record<string, string>, baseUrl: string): string[] {
  const lines: string[] = [];

  if (move.effects.length > 0) {
    // Use effects array — it has specific info like "Apply 2 Frail"
    for (const effect of move.effects) {
      // Skip redundant "N hits" lines — already shown in intent tag
      if (/^\d+(?:\/\d+)? hits?$/i.test(effect)) continue;
      lines.push(linkEffect(effect, powerSlugs, baseUrl));
    }
  } else {
    // Generate from intents when effects is empty
    for (const intent of move.intents) {
      switch (intent.type) {
        case 'attack':
        case 'SingleAttackIntent':
          if (intent.damage != null) {
            const dmg = formatScaled(intent.damage, intent.damage_base);
            lines.push(`Deal <span class="desc-red">${dmg}</span> damage`);
          }
          break;
        case 'multi_attack':
          if (intent.damage != null) {
            const dmg = formatScaled(intent.damage, intent.damage_base);
            const hitsStr = formatScaled(intent.hits, intent.hits_base);
            const hits = hitsStr ? ` x${hitsStr}` : '';
            lines.push(`Deal <span class="desc-red">${dmg}</span> damage${hits}`);
          }
          break;
        case 'block':
          if (intent.amount != null) {
            const amt = formatScaled(intent.amount, intent.amount_base);
            lines.push(`Gain <span class="desc-blue">${amt}</span> Block`);
          } else {
            lines.push(`<span class="desc-blue">Block</span>`);
          }
          break;
        case 'buff':
          lines.push(`<span class="desc-gold">Buff</span>`);
          break;
        case 'debuff':
          lines.push(`<span class="desc-purple">Debuff</span>`);
          break;
        case 'heal':
          lines.push(`<span class="desc-green">Heal</span>`);
          break;
        case 'stun':
          lines.push(`<span class="desc-blue">Stun</span>`);
          break;
        case 'sleep':
          lines.push(`<span class="desc-blue">Sleep</span>`);
          break;
        case 'summon':
          lines.push(`<span class="desc-green">Summon</span>`);
          break;
        case 'escape':
          lines.push(`Escape`);
          break;
        case 'status':
          lines.push(`<span class="desc-purple">Apply status</span>`);
          break;
        // hidden, death_blow — no additional description needed
      }
    }
  }

  return lines;
}

/**
 * Convert a single effect string to HTML with linked power names.
 */
// Numeric token in an effect string. Matches plain integers (e.g. "13") and
// ascension-scaled values rendered as "base/asc" (e.g. "12/13"), so the
// renderer can show both tiers without losing anything when scaling is
// present.
const SCALED_NUMBER = String.raw`\d+(?:\/\d+)?`;

function linkEffect(effect: string, powerSlugs: Record<string, string>, baseUrl: string): string {
  // "Apply N PowerName" or "Apply PowerName" (N may be "base/asc")
  const applyMatch = effect.match(new RegExp(`^(Apply)\\s+(${SCALED_NUMBER}\\s+)?(.+)$`));
  if (applyMatch) {
    const amount = applyMatch[2]?.trim();
    const name = applyMatch[3].trim();
    const slug = powerSlugs[name.toLowerCase()];
    const nameHtml = slug
      ? `<a href="${baseUrl}powers/${slug}/">${name}</a>`
      : `<span class="desc-gold">${name}</span>`;
    return amount
      ? `Apply <span class="desc-red">${amount}</span> ${nameHtml}`
      : `Apply ${nameHtml}`;
  }

  // "Gain N Block" or "Gain N PowerName"
  const gainMatch = effect.match(new RegExp(`^(Gain)\\s+(${SCALED_NUMBER})\\s+(.+)$`));
  if (gainMatch) {
    const amount = gainMatch[2];
    const name = gainMatch[3].trim();
    if (name === 'Block') {
      return `Gain <span class="desc-blue">${amount}</span> <span class="desc-blue">Block</span>`;
    }
    const slug = powerSlugs[name.toLowerCase()];
    const nameHtml = slug
      ? `<a href="${baseUrl}powers/${slug}/">${name}</a>`
      : `<span class="desc-gold">${name}</span>`;
    return `Gain <span class="desc-green">${amount}</span> ${nameHtml}`;
  }

  // "Deal N damage"
  const dealMatch = effect.match(new RegExp(`^Deal\\s+(${SCALED_NUMBER})\\s+damage$`));
  if (dealMatch) {
    return `Deal <span class="desc-red">${dealMatch[1]}</span> damage`;
  }

  // "Heal N"
  const healMatch = effect.match(/^Heal\s+(\d+)$/);
  if (healMatch) {
    return `Heal <span class="desc-green">${healMatch[1]}</span>`;
  }

  // "Add X to discard"
  const addMatch = effect.match(/^Add\s+(.+)\s+to\s+discard$/);
  if (addMatch) {
    return `Add <span class="desc-purple">${addMatch[1]}</span> to discard`;
  }

  // Fallback
  return effect;
}

/**
 * Parse move_pattern text into structured display steps.
 */
export function parsePattern(text: string): string[] {
  if (!text) return [];
  const clean = text.replace(/^"|"$/g, '');

  // Cycle pattern: "Cycles in the order: X -> Y -> Z."
  const cycleMatch = clean.match(/Cycles in the order:\s*(.+)/);
  if (cycleMatch) {
    const moves = cycleMatch[1].split('->').map(m => m.trim().replace(/\.$/, ''));
    return moves.map((m, i) => `${i + 1}. ${m}${i < moves.length - 1 ? ' \u2192' : ' (repeat)'}`);
  }

  // "Starts with X, then cycles: Y -> Z."
  const startCycleMatch = clean.match(/Starts with (.+?),\s*then cycles?:\s*(.+)/);
  if (startCycleMatch) {
    const start = startCycleMatch[1].trim().replace(/\.$/, '');
    const cycleMovesRaw = startCycleMatch[2].split('->').map(m => m.trim().replace(/\.$/, ''));
    const steps = [`1. ${start}`];
    cycleMovesRaw.forEach((m, i) => {
      steps.push(`${i + 2}. ${m}${i < cycleMovesRaw.length - 1 ? ' \u2192' : ' (repeat from 2)'}`);
    });
    return steps;
  }

  // Sequential: split on ". "
  const sentences = clean.split(/\.\s+/).filter(s => s.trim()).map(s => s.replace(/\.$/, '').trim());
  return sentences;
}

/**
 * Get the URL for an encounter. Single-monster encounters link directly
 * to the monster page (if it exists); multi-monster encounters (including
 * packs of the same monster) link to the encounter page.
 */
export function encounterHref(
  enc: { id: string; data: { monsters: Array<{ slug: string }>; total_monsters: number } },
  base: string,
  monsterIds?: Set<string>,
): string {
  if (enc.data.total_monsters === 1 && enc.data.monsters.length === 1) {
    const slug = enc.data.monsters[0].slug;
    if (!monsterIds || monsterIds.has(slug)) {
      return `${base}monsters/${slug}/`;
    }
  }
  return `${base}encounters/${enc.id}/`;
}

/**
 * Whether an encounter is a true single-monster encounter (not a pack).
 */
export function isSoloEncounter(enc: { data: { total_monsters: number } }): boolean {
  return enc.data.total_monsters === 1;
}
