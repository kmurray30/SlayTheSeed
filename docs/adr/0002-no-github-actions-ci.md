# No GitHub Actions CI — local gates are the only gate

There is deliberately no `.github/workflows/` directory. The repo is private,
so every Actions minute is billed, and a PR-only workflow gets no base cache
and recompiles the whole workspace cold — roughly 10–19 minutes per PR. That
exhausted the Actions budget once.

## Consequences

A missing `.github/workflows/` is the decision, not an oversight. It has
already been "restored" by mistake once (`571ba522e`, removed again by
`6efd5930`) — this ADR exists mainly so the next reader doesn't helpfully add
it back. Ask before adding one.

The local gates therefore carry the full load, including `cargo test
--workspace --doc`, which nextest cannot run and which nothing else covers.
