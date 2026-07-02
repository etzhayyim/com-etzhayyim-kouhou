# com-etzhayyim-kouhou

kouhou (広報) — public-interest / public-sector information curator + social
poster. See `README.md` for the core contract and full-repo `../../../CLAUDE.md`
"Actors" section for the pattern this follows (containment + independent
governor + append-only ledger). Superproject decision record:
`../../../90-docs/adr/2607022210-com-etzhayyim-kouhou-public-info-actor-r0.md`.
Design 正本: `docs/adr/0001-architecture.md`.

## Invariant

kouhou NEVER publishes a briefing the PublicInfoGovernor rejects. Every
published briefing cites a REGISTERED public-sector / public-interest source
(host whitelist; canonical registry `registry/sources.seed.json`). Unregistered-
source / no-provenance / commercial-content / catastrophe-veto / no-actuation
proposals are HELD — recorded as a hold in the append-only ledger, never
published. Only `:commit` writes the Store + publishes; every commit/hold is an
immutable ledger fact. Publication is AUTONOMOUS by default (ADR-2606281500,
種をまく) — no per-post operator/Council prior restraint; the off-switch is the
revocable member CACAO leash. Low-confidence briefings still publish, tagged
`:low-confidence`. Aggregate-first: one run publishes ONE briefing (no flood).

## Conventions

- `.cljc` for anything portable (operation/governor/advisor/publisher/phase/
  store/ingest/sim) — `.clj` only for JVM-only I/O (cacao, aozora).
- `kouhou.cacao` / `kouhou.aozora` are faithful ports of `tsumugu.cacao` /
  `tashikame.aozora` (self-sovereign Ed25519 identity + app-aozora createRecord).
- The actor's own Ed25519 identity lives in `.kouhou/identity.edn` (gitignored)
  — NEVER commit a private key.
- `clojure -M:lint` (clj-kondo, errors fail) / `clojure -M:dev:test`.
