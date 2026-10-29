# The C# decomp is the source of truth for game behaviour, not the Python sim

The Combat Engine was originally ported from a Python reimplementation
(`sts2-rl-agent`, pinned at `01c094d`), and the `// Ported from
sts2-rl-agent@…` headers still record that provenance. That sim was known to
diverge from the real game on specific mechanics. Every question about *what
the game does* is answered by grepping
`sts2-wiki/decompiled/v0.107.1/` instead.

## Consequences

The Python sim is archived and no longer checked out. It stays authoritative
for exactly one thing — how the combat loop was originally wired (hook
channels, command bus shape, draw-pile order) — and for nothing else. Monster
behaviour, card and power effects, and encounter composition are where it
drifts, so citing it there is the recurring failure mode this ADR is written
to stop.
