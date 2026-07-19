# com-etzhayyim-kouhou (広報)

**kouhou** — a public-interest / public-sector information curator + social
poster. It ingests news/info from **registered** public-sector and public-
interest sources (government PR, 独立行政法人, 公益法人, 官報), organizes each into a
faithful summary with provenance, and **publishes a social briefing to
app-aozora** (collection `com.etzhayyim.apps.kouhou.publicBriefing`).

**DID**: `did:web:etzhayyim.github.io:com-etzhayyim-kouhou` (depth-1 self-minted
`did:key` carried in the published record; RAD identity journal at
`orgs/etzhayyim/root/80-data/kotoba-rad/kouhou.identity.journal.edn`).
**Namespace**: `com.etzhayyim.apps.kouhou.*`.
**ADR**: ADR-2607022210 (superproject, R0 scaffold) + `docs/adr/0001-architecture.md` (正本).
**Status**: R0 scaffold — `mock-advisor` + `mock-publisher`; source registry
(`registry/sources.seed.json`) expanded 2026-07-19 from a Japan-only fictional
placeholder set to a small, spot-verified **world-scope** set (see "Source
registry" below — this is still R0, still not what the running governor
actually enforces yet); real LLM summarizer (`langchain.model` on Murakumo) +
real registry fetch + real aozora Publisher wired at deploy.
**First-touch channel**: app-aozora (`com.atproto.repo.createRecord`).
**Cross-actor**: distinct from **kawaraban** (general news mirror, no curation/
posting), **kataribe** (religious press), **danjo** (gov-data discrepancy
oversight). kouhou briefings are candidate inputs to **tashikame** (fact-check)
and **danjo** (discrepancy).

## Overview

kouhou is the etzhayyim organism's public-interest wire. Per the autonomous-
publication doctrine (ADR-2606281500, 種をまく), it publishes briefings
**autonomously by default** — no per-post operator/Council prior restraint. The
safety rails are the actor's OWN seed: a self-`did:key` + a revocable member
CACAO leash (off-switch) + a Rider §2 catastrophe-veto scan + a **source
whitelist** (only registered public-sector/public-interest sources).

Containment + independent governor + append-only ledger: the intelligence node
(`organizer`) is sealed into one graph node and returns a *proposal only*; an
independent **PublicInfoGovernor** censors it; only `:commit` writes the Store +
publishes. Publication is SPEECH, not ACTUATION. Aggregate-first: one run =
one briefing (no flood).

## StateGraph (one source digest = one run)

```
intake → advise(organizer) → govern(PublicInfoGovernor) → decide → commit | hold
```

No `interrupt-before` (autonomous). The PublicInfoGovernor's HARD violations
are the only thing that withholds publication.

| node | role |
|---|---|
| `:advise` | `organizer` (contained) — faithful summary + provenance + domain/tags. Proposal only. |
| `:govern` | `PublicInfoGovernor` — independent censor (separate system). |
| `:commit` | writes briefing to Store + append-only ledger; publishes to app-aozora when phase allows. |
| `:hold`   | records the rejection as a hold; no SSoT mutation, no publish. |

## PublicInfoGovernor gates

**HARD → HOLD (never publish):**
- `:no-actuation` — proposal `:effect ≠ :assessment`.
- `:no-provenance` — a briefing with a blank source-url.
- `:source-not-in-registry` — source host not in the public-sector/public-interest whitelist.
- `:commercial-content` — ad / sponsored markers (off-mission).
- `:catastrophe-veto` — Rider §2 catastrophe-veto scan hit.

**SOFT → publish with a transparency tag (not a block):**
- `:low-confidence` — overall confidence below floor; the briefing still publishes, tagged.

## Source registry

The canonical whitelist is `registry/sources.seed.json` (`source-id` / `name` /
`host` / `kind` / `domain` / `country` / `url` / `read-only` / `verified` /
`comment`).

**2026-07-19 (ADR-2607197800): generalized from Japan-only to world-scope.**
The original 3 entries were all fictional `*.example.*` placeholders (not
real hosts). They have been replaced with 8 entries spanning Japan (2),
the United States, the United Kingdom, Germany, France, the European
Commission, and the United Nations — each spot-checked with a direct live
HTTP fetch on 2026-07-19 and marked `"verified": true/false` honestly:

- `verified: true` (7 of 8) — a direct fetch on 2026-07-19 returned HTTP 200
  with real RSS/Atom/RDF feed content (not an HTML placeholder or error page).
  See each entry's `comment` for what was observed.
- `verified: false` (1 of 8, `kanpou` / 官報) — the official gazette site
  itself is real and live, but no machine-readable feed could be found at any
  of the common paths tried; it needs either a confirmed feed URL or a
  different ingest mechanism before it can be treated as live.

This is a **small, best-effort, spot-verified set — not a claim of exhaustive
world coverage.** Most of the world's governments and public bodies are not
yet represented here (that broader "collect everything" job belongs to
kawaraban's outlet allowlist and, longer-term, mikurabe per ADR-2607197800);
this registry only needed to stop being Japan-only fiction. Gaps should be
filled incrementally and honestly flagged (`"verified": false` + a comment
explaining what's unconfirmed), never silently guessed. The old
`koueki-hojin` (公益法人協会) fictional entry/category was dropped rather than
re-guessed with a fake host — a real 公益法人-adjacent source can be added
later following the same honesty discipline.

**Important limitation — this file is not yet what the running governor
enforces.** `kouhou.governor/default-registry` (in `src/kouhou/governor.cljc`,
the host set `PublicInfoGovernor/check` actually uses at R0, and what the test
suite exercises) still contains only the OLD fictional `.example.` hosts. It
was intentionally left unchanged by this registry expansion (governor/ingest
decision logic is out of scope for a registry-data-only change). Per
`kouhou.ingest`'s own docstring, the real JVM loader that would read
`registry/sources.seed.json` and feed it into the governor at deploy is not
wired yet ("kept out of R0 so the core stays offline-testable"). So today,
this file is documentation/staging data for that future wiring — the actual
whitelist a live run checks against is still `default-registry`'s fictional
set, until that loader lands. `kouhou.ingest/registered-source?` remains the
host-check function.

## Phase rollout

| Phase | label | publish? |
|---|---|---|
| 0 | observe | no — governor-clean briefings recorded only (shadow) |
| 1 | autonomous-publish (**default**, 種をまく) | yes |

## Injected seams (each a swap, core unchanged)

- **Store** — `MemStore` ‖ `DatomicStore` (langchain.db `:db-api`) ‖ kotoba-server pod.
- **Advisor** — `mock-advisor` (deterministic) ‖ real LLM on `langchain.model` / Murakumo.
- **Publisher** — `MockPublisher` ‖ real app-aozora createRecord (`kouhou.aozora`).
- **Phase** — 0 observe → 1 autonomous-publish.
- **Registry** — public-sector host whitelist (context :registry override).

## Run

```bash
clojure -M:lint          # clj-kondo, errors fail
clojure -M:dev:test      # cognitect test-runner (canonical)
clojure -M:dev:run       # offline demo (one registered + one unregistered source)
```

## Related files

- `docs/adr/0001-architecture.md` — design 正本.
- `../../../90-docs/adr/2607022210-com-etzhayyim-kouhou-public-info-actor-r0.md` — superproject ADR.
- `CLAUDE.md` — repo invariants / conventions.
