# champions.toml is the single source of truth, and generates its consumers

Several lanes must know which run policy serves each character — the Python
loader, both workbench inference backends, the advisor spec, the Rust match
arms, and the `Dockerfile`. We keep that list once, in
`sts2-pycli/data/ppo/champions.toml`, and *generate* the two consumers that
cannot read it at runtime (a Rust worktree has no sibling `sts2-pycli`; `COPY`
is Dockerfile syntax) into marked regions checked byte-for-byte by
`just check-champions`.

## Considered options

The previous design was four hand-kept lists, whose Python member *globbed*
for the newest net present on disk. It shipped a container silently serving a
retired net (PR #339) and nearly did so again (#404, #402). Globbing means a
missed edit degrades quietly; exact filenames plus a byte-check gate make a
stale net inert and a missing champion a loud error.

## Consequences

Never hand-edit inside the generated markers — the gate will fail and the edit
will be overwritten. Promotion is one command: `just promote-champion
<character> <onnx>`.
