"""R5b map representation — the routing lever (Daniel's +9.8pp).

From a ``Decision`` (act-map graph + current coord + offered move options),
compute per-node and per-option features that turn "what does this path lead
to?" from a multi-hop attention program into a lookup:

- per node: room type, ego-relative (dx,dy)=node-current, forward-reachable
  flag, and the [0,1]-scaled reverse-topo DAG aggregates
  (min/max elites-ahead, distance-to-rest);
- per move option: destination room + the destination node's forward-cone
  aggregate (so a single token says "this path leads toward 2 elites / a rest
  in 3 rows");
- for the act: the identity of the boss the map terminates in
  (``MapFeatures.boss_idx``), which the encoder puts on the state token of
  every decision so deck-shaping can condition on the fight it is preparing
  for.

All aggregates are scaled to [0,1] by ``MAP_AGG_CAP`` — Daniel found unscaled
magnitudes drown the additive token sum (~40pp regression).
"""

from __future__ import annotations

from dataclasses import dataclass

from . import vocab

MAP_AGG_CAP = 15  # dist-to-rest cap and the [0,1] scale divisor


@dataclass
class NodeFeat:
    coord: tuple[int, int]
    room_idx: int
    dx: float
    dy: float
    reachable: float  # 0/1
    min_elites: float  # scaled [0,1]
    max_elites: float
    dist_rest: float
    # Boss-registry index on the terminal boss node; the pad row on every
    # other node. See `MapFeatures.boss_idx`.
    boss_idx: int


@dataclass
class OptionConeFeat:
    dest_room_idx: int
    dx: float
    dy: float
    min_elites: float
    max_elites: float
    dist_rest: float


@dataclass
class MapFeatures:
    nodes: list[NodeFeat]
    # Aligned 1:1 with a map Decision's ``options`` (empty for card decisions).
    options: list[OptionConeFeat]
    # Boss-registry index for the boss at the top of THIS act's map, or the
    # pad row when the engine could not resolve one. The encoder writes it on
    # the state token of every decision, which is what lets a card-removal or
    # upgrade choice be made with the upcoming boss in view.
    boss_idx: int


def _dag_aggregates(
    nodes: dict[tuple[int, int], dict],
) -> dict[tuple[int, int], tuple[int, int, int]]:
    """Reverse-topo DP over rows: (min_elites, max_elites, dist_rest) per node.
    elites-ahead = (1 if this node is an elite) + min/max over live children;
    dist_rest = 0 at a rest node else 1 + min over children, capped."""
    agg: dict[tuple[int, int], tuple[int, int, int]] = {}
    # Process rows high→low so children are done before parents. Restrict to
    # children already aggregated (`in agg`), not merely `in nodes`: a child on
    # the same row as its parent — or any forward edge that the row ordering
    # hasn't reached yet — would otherwise index `agg` before it exists and
    # raise KeyError (observed on some maps, surfacing as an A10 run crash).
    for coord in sorted(nodes, key=lambda c: -c[1]):
        info = nodes[coord]
        is_elite = 1 if info["type"] == "elite" else 0
        is_rest = info["type"] == "rest_site"
        children = [c for c in info["children"] if c in agg]
        if not children:
            mn = mx = is_elite
            dr = 0 if is_rest else MAP_AGG_CAP
        else:
            cmins = [agg[c][0] for c in children]
            cmaxs = [agg[c][1] for c in children]
            cdr = [agg[c][2] for c in children]
            mn = is_elite + min(cmins)
            mx = is_elite + max(cmaxs)
            dr = 0 if is_rest else min(MAP_AGG_CAP, 1 + min(cdr))
        agg[coord] = (mn, mx, dr)
    return agg


def _reachable(
    nodes: dict[tuple[int, int], dict],
    seeds: set[tuple[int, int]],
) -> set[tuple[int, int]]:
    """Forward flood-fill of nodes reachable from ``seeds`` (inclusive)."""
    seen: set[tuple[int, int]] = set()
    stack = [s for s in seeds if s in nodes]
    while stack:
        n = stack.pop()
        if n in seen:
            continue
        seen.add(n)
        for c in nodes[n]["children"]:
            if c in nodes and c not in seen:
                stack.append(c)
    return seen


def _scale(v: int) -> float:
    return min(1.0, max(0.0, v / MAP_AGG_CAP))


def _pascal_to_snake(t: str) -> str:
    """full_map()'s UI PascalCase room type → vocab snake form
    (``"RestSite"`` → ``"rest_site"``)."""
    out: list[str] = []
    for i, ch in enumerate(t):
        if ch.isupper() and i > 0:
            out.append("_")
        out.append(ch.lower())
    return "".join(out)


def boss_id_of(full_map: dict | None) -> str:
    """The current act's boss encounter id (``"THE_KIN_BOSS"``), or ``""``.

    ``full_map()["boss"]["id"]`` is the engine's own resolution chain (pinned
    boss > generated roll > single-boss-pool fallback), so it is present from
    the first floor of every act — the boss is decided at map-generation time,
    long before the player walks to it. Empty means the engine could not
    resolve one, which the caller turns into the registry's pad row rather
    than guessing.
    """
    boss = (full_map or {}).get("boss") or {}
    return str(boss.get("id") or "")


def nodes_from_full_map(full_map: dict | None) -> dict[tuple[int, int], dict]:
    """Convert ``PyRunManager.full_map()``'s nested-rows schema into the
    ``{(col,row): {"type": snake, "children": [(col,row)...], "boss_id": str}}``
    dict the DAG DP consumes. Includes the terminal boss node (no children);
    top-row nodes get a synthetic edge to it (the UI schema omits that edge) so
    forward cones and reachability see through to the boss.

    The boss node keeps its ``boss_id``. It used to be dropped here — the node
    was rewritten as a bare ``{"type": "boss"}`` — which is why the identity of
    the act's boss never reached the policy despite the engine supplying it on
    every observation.
    """
    nodes: dict[tuple[int, int], dict] = {}
    if not full_map:
        return nodes
    for row in full_map.get("rows") or []:
        for n in row:
            coord = (int(n["col"]), int(n["row"]))
            nodes[coord] = {
                "type": _pascal_to_snake(str(n["type"])),
                "children": [(int(c["col"]), int(c["row"])) for c in n.get("children") or []],
                "boss_id": "",
            }
    boss = full_map.get("boss")
    if boss:
        bcoord = (int(boss["col"]), int(boss["row"]))
        top_y = max((c[1] for c in nodes), default=-1)
        nodes[bcoord] = {"type": "boss", "children": [], "boss_id": boss_id_of(full_map)}
        for coord, info in nodes.items():
            if coord[1] == top_y and coord != bcoord and not info["children"]:
                info["children"] = [bcoord]
    return nodes


def compute(
    full_map: dict | None,
    map_coord: tuple[int, int] | None,
    move_cols: list[int],
) -> MapFeatures:
    """Build R5b features from a ``ppo.env.Decision``'s obs fields.

    ``move_cols`` are the offered ``Move{col}`` destination columns in
    action order (empty for non-path decisions). Destination row = current
    row + 1, or row 0 at act start (``map_coord`` None) — MapChoice
    semantics.
    """
    nodes = nodes_from_full_map(full_map)
    boss_pad = vocab.bosses().size
    raw_boss = boss_id_of(full_map)
    boss_idx = vocab.bosses().index(raw_boss) if raw_boss else boss_pad
    cur: tuple[int, int] | None = (
        (int(map_coord[0]), int(map_coord[1])) if map_coord is not None else None
    )

    agg = _dag_aggregates(nodes)

    dest_row = (cur[1] + 1) if cur is not None else 0
    opt_dests: list[tuple[int, int]] = [(int(c), dest_row) for c in move_cols]

    seeds = {d for d in opt_dests if d in nodes}
    if cur is not None and cur in nodes:
        seeds.add(cur)
    reachable = _reachable(nodes, seeds) if seeds else set(nodes)

    cx, cy = cur if cur is not None else (-1, -1)
    node_feats: list[NodeFeat] = []
    for coord, info in nodes.items():
        mn, mx, dr = agg[coord]
        node_feats.append(
            NodeFeat(
                coord=coord,
                room_idx=vocab.room_index(info["type"]),
                dx=float(coord[0] - cx),
                dy=float(coord[1] - cy),
                reachable=1.0 if coord in reachable else 0.0,
                min_elites=_scale(mn),
                max_elites=_scale(mx),
                dist_rest=_scale(dr),
                boss_idx=(vocab.bosses().index(info["boss_id"]) if info["boss_id"] else boss_pad),
            )
        )

    opt_feats: list[OptionConeFeat] = []
    for d in opt_dests:
        mn, mx, dr = agg.get(d, (0, 0, MAP_AGG_CAP))
        dest_room = nodes[d]["type"] if d in nodes else "unknown"
        opt_feats.append(
            OptionConeFeat(
                dest_room_idx=vocab.room_index(dest_room),
                dx=float(d[0] - cx),
                dy=float(d[1] - cy),
                min_elites=_scale(mn),
                max_elites=_scale(mx),
                dist_rest=_scale(dr),
            )
        )

    return MapFeatures(nodes=node_feats, options=opt_feats, boss_idx=boss_idx)
