(ns kouhou.run-live-ingest
  "Non-interactive live-ingest entrypoint — the real-registry-fetch gap this
  namespace closes (README.md's own R0 status line used to read '... real
  registry fetch + real aozora Publisher wired at deploy'; the aozora
  Publisher half was already real via `kouhou.aozora`/`kouhou.deploy`, this
  closes the registry-fetch half). Mirrors kawaraban's already-landed
  `kawaraban.run-live-ingest` shape (ADR-2607110200) and safety engineering:
  the `KOUHOU_ALLOW_LIVE_INGEST` gate (checked inside `live-fetch/fetch-source!`,
  this script does not set it — an operator/workflow env config does), a
  spaced-out inter-source delay (same order of magnitude as kawaraban's
  measured aozora-graph read-latency mitigation, ADR-2607110200 addendum 2:
  a burst of writes there pushed shared PDS read latency from ~4s to ~50s),
  and per-source error isolation (`run-source!` never lets one source's HTTP
  failure or exception abort the rest of the run).

  Aggregate-first, ONE run = ONE briefing PER SOURCE (kouhou's own doctrine —
  README.md 'Invariant' + 'StateGraph' sections, docs/adr/0001-architecture.md
  §3, CLAUDE.md): `live-fetch/fetch-source!` already reduces each source's
  feed down to its single most-recent item before it ever reaches the actor,
  so this entrypoint runs exactly one full `kouhou.operation` StateGraph
  execution (:advise -> :govern -> :decide -> :commit|:hold) per source per
  invocation — never one execution per feed item. It is NOT a flood-publisher
  just because it fetches multiple sources; each source still only ever
  contributes at most one briefing.

  Uses the framework DEFAULT `mock-advisor` (deterministic faithful-excerpt
  summarizer, see `kouhou.advisor`) — the SAME advisor `kouhou.operation/build`
  already defaults to when no `:advisor` is given, so a live run's curation
  behavior is not a new code path, only its INPUT (a real fetched item
  instead of a hand-written fixture) is new. A real-LLM organizer already
  exists and is proven live (`kouhou.deploy/llm-advisor` against Murakumo,
  ADR-2607173100's `murakumo-main` alias applies there) — swapping it in here
  is a separate, deliberately out-of-scope seam change (`op/build`'s
  `:advisor` opt), not exercised by this entrypoint by default.

  Every governor HOLD is reported honestly, never worked around — a HELD
  source's briefing is simply not published; that is the PublicInfoGovernor
  functioning correctly, not a bug in this script.

  Usage:  KOUHOU_ALLOW_LIVE_INGEST=1 clojure -M:live-ingest
  Env:    KOUHOU_ALLOW_LIVE_INGEST  the live-fetch gate (kouhou.live-fetch)
          KOUHOU_REGISTRY_PATH      default \"registry/sources.seed.json\"
          KOUHOU_PDS                default kouhou.aozora/default-pds
          KOUHOU_IDENTITY_PATH      default \".kouhou/identity.edn\""
  (:require [clojure.data.json :as json]
            [langgraph.graph :as g]
            [kouhou.aozora :as aozora]
            [kouhou.cacao :as cacao]
            [kouhou.live-fetch :as live-fetch]
            [kouhou.operation :as op]
            [kouhou.store :as store])
  (:gen-class))

(def ^:private inter-source-delay-ms
  "Spaced out, not fired back-to-back — kawaraban's own live-ingest run
  (ADR-2607110200 addendum 2) measured a single outlet's write burst alone
  pushing the shared aozora operator graph's read latency from ~4s to ~50s;
  this reuses the same order of magnitude to avoid concentrating kouhou's own
  writes (up to one createRecord per verified source) into one burst."
  3000)

(defn- pds []
  (or (System/getenv "KOUHOU_PDS") aozora/default-pds))

(defn- identity-path []
  (or (System/getenv "KOUHOU_IDENTITY_PATH") ".kouhou/identity.edn"))

(defn real-publisher
  "kouhou.aozora's real app-aozora Publisher, bound to the actor's own
  self-sovereign identity (fresh-minted on first run if `.kouhou/identity.edn`
  does not yet exist locally — `cacao/load-or-create-identity!`)."
  [identity]
  (aozora/aozora-publisher {:pds        (pds)
                            :identity   identity
                            :json-write json/write-str
                            :json-read  json/read-str}))

(defn run-source!
  "One source's fetch -> `kouhou.operation` actor graph run. Returns a result
  map; NEVER throws — any exception (fetch, parse, or graph-node failure) is
  caught and reported as `:error`, so a caller iterating over many sources is
  never aborted by a single bad one.

  `fetch-fn`/`allowed?` are injectable (default the real HTTP GET + the real
  `KOUHOU_ALLOW_LIVE_INGEST` env check) so tests can exercise this fn with
  zero network I/O, the same seam `kouhou.live-fetch/fetch-source!` itself
  exposes."
  ([source hosts actor] (run-source! source hosts actor live-fetch/jvm-http-get (live-fetch/live-allowed?)))
  ([source hosts actor fetch-fn allowed?]
   (try
    (let [{:keys [refused reason item fetch-error]}
          (live-fetch/fetch-source! source hosts fetch-fn allowed?)
          sid (:source-id source)]
      (cond
        refused
        {:source-id sid :refused true :reason reason}

        fetch-error
        {:source-id sid :fetch-error fetch-error}

        (nil? item)
        {:source-id sid :fetch-error "fetch-source! returned no item and no fetch-error (unexpected)"}

        :else
        (let [req {:op :source/digest :source-id sid :url (:url item)
                   :title (:title item) :raw (:raw item)}
              r   (g/run* actor
                          {:request req :context {:actor-id "kouhou" :phase 1 :registry hosts}}
                          {:thread-id sid})
              disp (get-in r [:state :disposition])]
          {:source-id  sid
           :disposition disp
           :published?  (boolean (get-in r [:state :published]))
           :pub         (get-in r [:state :published])
           :item-url    (:url item)
           :item-title  (:title item)})))
    (catch Exception e
      {:source-id (:source-id source) :error (.getMessage e)}))))

(defn run-all!
  "One live-ingest pass over every `:verified` source in the registry at
  `registry-path`. `on-result` (default no-op) is called with each source's
  result map as soon as it finishes — visible progress per source rather than
  one buffered println at the end, same reasoning as kawaraban's
  `run-all!` (ADR-2607110200 addendum 2: a silent, fully-buffered run is
  indistinguishable from a hang from the outside)."
  ([registry-path] (run-all! registry-path (fn [_])))
  ([registry-path on-result]
   (let [registry (live-fetch/load-registry registry-path json/read-str)
         hosts    (live-fetch/registry->host-set registry)
         sources  (live-fetch/verified-sources registry)
         id       (cacao/load-or-create-identity! (identity-path))
         pub      (real-publisher id)
         s        (store/seed-db)
         actor    (op/build s {:publisher pub})]
     {:identity id
      :results
      (mapv (fn [source]
              (when (pos? inter-source-delay-ms) (Thread/sleep inter-source-delay-ms))
              (let [result (run-source! source hosts actor)]
                (on-result result)
                result))
            sources)})))

(defn -main [& _]
  (let [registry-path (or (System/getenv "KOUHOU_REGISTRY_PATH") "registry/sources.seed.json")
        {:keys [identity results]} (run-all! registry-path (fn [r] (println (pr-str r)) (flush)))
        committed (filter #(= :commit (:disposition %)) results)
        held      (filter #(= :hold (:disposition %)) results)
        published (filter :published? results)
        errors    (filter #(or (:error %) (:fetch-error %)) results)]
    (println "=== kouhou live-ingest ===")
    (println "actor did:key:" (:did identity))
    (println (str (count results) " sources, "
                   (count committed) " committed, "
                   (count held) " held, "
                   (count published) " published, "
                   (count errors) " with errors"))
    (when (and (seq errors) (= (count errors) (count results)))
      ;; every single source errored -- likely a systemic problem (network,
      ;; gate, identity), not per-source flakiness -- fail the run loudly
      ;; instead of a silent green no-op
      (System/exit 1))))
