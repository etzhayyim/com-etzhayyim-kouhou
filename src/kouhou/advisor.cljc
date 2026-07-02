(ns kouhou.advisor
  "organizer — the *contained intelligence node* for kouhou. It takes a fetched
  source item (url + title + raw text, from a registered public-sector /
  public-interest source) and returns a PROPOSAL: one curated briefing (a
  faithful summary + provenance URL + domain/tags), with a confidence. It NEVER
  returns a committed record and NEVER decides publication — the
  PublicInfoGovernor censors every proposal downstream, and only :commit writes
  the SSoT + publishes. Mirrors the `Advisor` protocol shape used by
  tashikame.advisor / tsumugu.mangallm.

  Sealed by construction: the default `mock-advisor` is deterministic (no
  non-deterministic free-write). The real advisor wires `langchain.model`
  against the Murakumo fleet (DEFAULT-PREFERRED per Rider v3.3 §2(i)) — still
  proposal-only, still governor-censored. Aggregate-first: one run = one
  briefing per source digest (not a flood).

  Proposal shape:
    {:summary    str
     :rationale  str
     :briefings  [{:title :summary :source-url :domain :tags :confidence}]
     :effect     :assessment   ; kouhou only ever curates, never actuates
     :confidence 0..1}"
  (:require [clojure.string :as str]))

(defprotocol Advisor
  (-organize [advisor store request] "store + request → proposal map"))

(defn- summarize [raw]
  ;; R0 mock: first ~60 chars of the raw text, ellipsized. Real advisor uses an
  ;; LLM summarizer (faithful, no embellishment — governor checks provenance).
  (let [s (str/trim (str raw ""))]
    (if (str/blank? s) "(要約なし)"
        (str (subs s 0 (min (count s) 60)) (when (> (count s) 60) "…")))))

(defn- organize* [{:keys [url title raw source-id]}]
  (cond
    (str/blank? url)
    {:summary "no source url" :rationale "no provenance" :briefings []
     :effect :noop :confidence 0.0}

    :else
    {:summary (str "curated briefing for " (or source-id url))
     :rationale "mock organizer: faithful summary + provenance"
     :briefings [{:title      (or (str/trim (str title "")) "(無題)")
                  :summary    (summarize raw)
                  :source-url url
                  :domain     :general
                  :tags       []
                  :confidence 0.8}]
     :effect :assessment :confidence 0.8}))

(defn mock-advisor
  "The deterministic organizer (default everywhere — no non-deterministic LLM
  free-write). Real-LLM wiring is a swap via `langchain.model` on Murakumo."
  []
  (reify Advisor (-organize [_ _store req] (organize* req))))

(defn trace
  "Decision-grounded audit record (evaluation appeals, publish audits)."
  [request proposal]
  {:t          :organizer-proposal
   :op         (:op request)
   :source-id  (:source-id request)
   :summary    (:summary proposal)
   :briefings  (:briefings proposal)
   :confidence (:confidence proposal)})
