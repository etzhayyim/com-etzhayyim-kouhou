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

## Source registry (2026-07-19, ADR-2607197800)

`registry/sources.seed.json` was generalized from a Japan-only fictional
`*.example.*` placeholder set to a small, spot-verified **world-scope** set
(JP ×2, US, GB, DE, FR, EU, UN). 7 of 8 entries were confirmed live via a
direct HTTP fetch on 2026-07-19 (real RSS/Atom/RDF content, not HTML); the
8th (`kanpou`/官報) is honestly marked `"verified": false` — the site is real
but no feed was found. This is still R0, still a small best-effort set, NOT
exhaustive world coverage — see the "Source registry" section of `README.md`
for the full honesty accounting. Do not silently mark a new entry `verified`
without an actual fetch. **This change did NOT touch `src/kouhou/governor.cljc`
or `src/kouhou/ingest.cljc`** — `kouhou.governor/default-registry` (what the
running governor and test suite actually check against at R0) still has the
OLD fictional hosts; syncing it to this file is a separate, not-yet-done step.
