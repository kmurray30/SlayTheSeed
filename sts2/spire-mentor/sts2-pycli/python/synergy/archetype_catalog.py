"""Archetype-catalog assembly for the workbench (spec §9/§10).

Merges the discovery output (``emit-archetypes`` → candidate cards + provenance
tag) with the combat dose-response curves (``emit-dose``) into the UI-facing
``archetypes_<char>.json`` document the workbench consumes (``synergy/web/src/
archetype.ts``): one archetype per candidate with its cards, tag, and measured
dose-response curve.

The Python tags (``confirmed-archetype`` / ``folklore`` / ``undiscovered``) are
mapped to the UI tags (``confirmed`` / ``folklore`` / ``undiscovered``).
"""

from __future__ import annotations

# Python discovery tag → UI tag (the UI has no "-archetype" suffix).
_TAG_MAP = {
    "confirmed-archetype": "confirmed",
    "confirmed": "confirmed",
    "folklore": "folklore",
    "undiscovered": "undiscovered",
}


def _curve_points(dose: dict) -> list[dict]:
    """Zip an ``emit-dose`` doc's ``v_curve``/``additive_curve`` into curve points."""
    v = dose.get("v_curve", []) or []
    add = dose.get("additive_curve", []) or []
    n = min(len(v), len(add))
    return [{"k": k, "v": float(v[k]), "additive": float(add[k])} for k in range(n)]


def _cards(candidate: dict) -> list[dict]:
    """Candidate ``cards`` (list[str]) → ``[{token, weight}]`` (uniform when
    per-card weights were not retained through the discovery merge)."""
    raw = candidate.get("cards", [])
    if raw and isinstance(raw[0], dict):
        return [{"token": c["token"], "weight": float(c.get("weight", 1.0))} for c in raw]
    return [{"token": c, "weight": 1.0} for c in raw]


def assemble_archetypes(archetypes_doc: dict, dose_doc: dict | None = None) -> dict:
    """Merge ``archetypes_<char>`` + ``dose_<char>`` into the UI archetype schema.

    Args:
        archetypes_doc: ``emit-archetypes`` output (``{character, candidates:
            [{id?, cards, tag, ...}]}``).
        dose_doc: Optional ``emit-dose`` output (``{character, doses:
            [{cards, tag, v_curve, additive_curve, target_k, ...}]}``).  Curves are
            matched to candidates by their (sorted) card set.

    Returns:
        ``{character, archetypes: [{id, label, tag, cards:[{token,weight}],
        curve:{targetK, points:[{k,v,additive}]}}]}`` — the workbench schema.
    """
    character = archetypes_doc.get("character", "ironclad")
    # Index dose curves by sorted card-set for a stable match to candidates.
    dose_by_cards: dict[frozenset[str], dict] = {}
    for d in (dose_doc or {}).get("doses", []):
        dose_by_cards[frozenset(d.get("cards", []))] = d

    archetypes = []
    for i, cand in enumerate(archetypes_doc.get("candidates", [])):
        cards = _cards(cand)
        tokens = frozenset(c["token"] for c in cards)
        dose = dose_by_cards.get(tokens, {})
        arch_id = cand.get("id") or f"arch-{i}"
        label = cand.get("label") or arch_id.replace("-", " ").replace("_", " ").title()
        archetypes.append(
            {
                "id": arch_id,
                "label": label,
                "tag": _TAG_MAP.get(cand.get("tag", ""), "folklore"),
                "cards": cards,
                "curve": {
                    "targetK": int(dose.get("target_k", 0)),
                    "points": _curve_points(dose),
                },
            }
        )
    return {"character": character, "archetypes": archetypes}
