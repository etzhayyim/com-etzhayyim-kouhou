# ADR-0001: kouhou (広報) — public-interest info curator + social poster

**Status**: R0 scaffold (2026-07-02)
**Deciders**: Jun Kawasaki
**Superproject ADR**: `90-docs/adr/2607022210-com-etzhayyim-kouhou-public-info-actor-r0.md`

## Context

The etzhayyim organism had no single actor that **organizes public-interest /
public-sector information into clean social posts**. Adjacent actors exist:

- **kawaraban** — news MEDIUM, mirrors news; by charter does not curate or post.
- **kataribe** — religious-corp press / publishing; different domain.
- **danjo** — public-accountability oversight over government DATA; detects
  discrepancies, does not post curated briefings to a feed.

Owner request (2026-07-02): design an actor that **organizes 公益 / 国営団体
news & info and posts it** (social post), alongside tashikame (fact-check).

## Decision

kouhou — the workspace-actor pattern's public-info-curation instance (same shape
as tashikame / robotaxi-actor / sng / etc.): containment + independent governor
+ append-only ledger.

1. **Containment + independent governor.** `organizer` (the intelligence node)
   is sealed into one graph node (`:advise`) and returns a *proposal only*
   (`:briefings` = faithful summary + provenance URL + domain/tags,
   `:effect :assessment`). An independent **PublicInfoGovernor** censors it;
   only `:commit` writes the Store + publishes. Invariant: *kouhou never
   publishes a briefing the PublicInfoGovernor rejects.*

2. **langgraph-clj StateGraph, 1 run = 1 source digest.** No unbounded inner
   loop. No `interrupt-before`.

3. **Autonomous publication (ADR-2606281500, 種をまく).** Publication is the
   actor's own SPEECH, autonomous by default — no per-post operator/Council
   prior restraint. The off-switch is the revocable member CACAO leash
   (`:leash` on the createRecord). PUBLICATION ≠ ACTUATION: kouhou only ever
   `:effect :assessment`. Aggregate-first: one run publishes ONE briefing.

4. **PublicInfoGovernor gates.**
   - HARD → HOLD (recorded, never published): `:no-actuation`, `:no-provenance`
     (blank source-url), `:source-not-in-registry` (host not whitelisted),
     `:commercial-content` (ad/sponsored markers — off-mission),
     `:catastrophe-veto` (Rider §2 scan).
   - SOFT → publish with tag: `:low-confidence`.

5. **Source whitelist (the load-bearing rule).** Only registered public-sector
   / public-interest sources may feed a published briefing. The canonical
   registry is `registry/sources.seed.json` (host / kind / url / read-only);
   `kouhou.governor/default-registry` mirrors the host set for offline tests;
   `kouhou.ingest/registered-source?` is the host check. Read-only public fetch
   is autonomously allowed (no-server-key read-only, ADR-2606072802) — never
   gate a read-only public ingest behind an operator step.

6. **Injected seams.** Store (`MemStore` ‖ `DatomicStore` ‖ kotoba-server) /
   Advisor (`mock` ‖ real `langchain.model` on Murakumo) / Publisher
   (`MockPublisher` ‖ real app-aozora createRecord) / Phase (0 observe → 1
   autonomous-publish) / Registry (host whitelist). Core is invariant across
   all swaps.

7. **Store is `:db-api` driven** (`{:q :transact! :db :pull :entid}`); a
   `MemStore ≡ DatomicStore` contract test guards it.

8. **Self-sovereign identity.** `kouhou.cacao` / `kouhou.aozora` (ported from
   `tsumugu.cacao` / `tashikame.aozora`) — depth-1 self-minted CACAO. Private
   key in `.kouhou/identity.edn` (gitignored).

9. **`.cljc` portable** (JVM/SCI/cljs/WASM); `.clj` only for JVM-only I/O.

## Consequences

- (+) A registered public-sector source can be summarized + posted without
  per-post human gating, while every published briefing is governor-clean,
  provenance-traced, and append-only audited — and never carries advertising.
- (+) `organizer` is upgrade/swap-able (mock → Murakumo LLM) without touching
  the publication guarantee.
- (−) R0 registry is illustrative; deploy must curate real official feeds with
  the source-Council seat. R0 summarizer is heuristic (first-N-chars); real
  faithful summarization needs the LLM wired. Catastrophe-veto denylist is
  illustrative until the canonical `etzhayyim_organism.sensors.charter_rider.scan`
  is wired.

## Alternatives considered

- **Merge into kawaraban** — rejected: kawaraban is mirror-only by charter
  (`G1 mirror-not-adjudicator`); curation + posting is a different role.
- **Council-gate every briefing** — rejected: contradicts ADR-2606281500
  autonomous publication for SPEECH; the leash is the off-switch.
- **One combined fact-check + curation actor with tashikame** — rejected: keep
  adjudicative speech (fact-check) and faithful summarization as separate
  actors with separate governors, per the one-actor-one-role charter.
