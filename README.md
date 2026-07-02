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
**Status**: R0 scaffold — `mock-advisor` + `mock-publisher` + illustrative
registry; real LLM summarizer (`langchain.model` on Murakumo) + real registry
fetch + real aozora Publisher wired at deploy.
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

The canonical whitelist is `registry/sources.seed.json` (`source-id` / `host` /
`kind` / `url` / `read-only`). R0 ships an illustrative set; deploy replaces
these with real official feeds. `kouhou.governor/default-registry` mirrors the
host set for offline tests; `kouhou.ingest/registered-source?` is the host check.

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
