(ns kouhou.governor
  "PublicInfoGovernor — the independent censor that earns a kouhou proposal the
  right to publish. SEPARATE system from the organizer advisor (defense in
  depth). Mirrors tashikame.governor's shape (HARD → HOLD, no override;
  SOFT → recorded warning, still publishes).

  Doctrine: publication is AUTONOMOUS by default (ADR-2606281500, 種をまく).
  The PublicInfoGovernor is NOT an external operator/Council prior restraint —
  it is kouhou's OWN seed rail (the off-switch is the revocable member CACAO
  leash, not a per-post approval). So:

    HARD violations → HOLD (recorded as a hold, NEVER published)
    SOFT (low conf) → publish, with a :low-confidence transparency tag

  HARD (never publish):
    :no-actuation          proposal :effect ≠ :assessment (kouhou only curates)
    :no-provenance         a briefing with a blank source-url
    :source-not-in-registry a briefing whose source host is NOT in the
                            whitelisted public-sector / public-interest registry
    :commercial-content    ad / sponsored markers in the briefing (off-mission)
    :catastrophe-veto      Rider §2 catastrophe-veto scan hits on briefing text
  SOFT (publish with tag):
    :low-confidence        overall confidence < floor"
  (:require [clojure.string :as str]))

(def confidence-floor 0.4)

(def default-registry
  "Whitelisted public-sector / public-interest source HOSTS (R0 illustrative —
  the canonical registry is registry/sources.seed.json, loaded by kouhou.ingest
  at deploy). A briefing whose source host is NOT here is held
  (:source-not-in-registry). Tests may override via context :registry."
  #{"press.example.go.jp" "kanpou.example.go.jp" "koueki.example.or.jp"
    "example.go.jp" "example.gov"})

(def ^:private catastrophe-denylist
  ["<CAT>" "<CSAM>" "<FORCE>" "<SURVEIL>"])

(def ^:private commercial-markers
  "Off-mission commercial / ad markers (R0 heuristic). Public-interest briefing
  must not carry advertising; aggregate-first, no attention extraction."
  ["<AD>" "<SPONSORED>" "<PR-PAID>"])

(defn host-of
  "Host of an absolute http(s) URL, or nil. Portable (.cljc) — regex, no JDK."
  [url]
  (when (string? url)
    (second (re-find #"(?i)^https?://([^/]+)" url))))

(defn- blob [briefings ks]
  (->> briefings (mapcat #(map % ks)) (filter string?) (str/join " ")))

(defn- catastrophe? [briefings]
  (let [b (blob briefings [:title :summary :tags])]
    (some #(str/includes? b %) catastrophe-denylist)))

(defn- commercial? [briefings]
  (let [b (blob briefings [:title :summary])]
    (some #(str/includes? b %) commercial-markers)))

(defn- unregistered? [registry briefing]
  (let [url (:source-url briefing)]
    (and (not (str/blank? url))
         (let [h (host-of url)] (or (nil? h) (not (contains? registry h)))))))

(defn check
  "Censors a kouhou proposal. Returns {:ok? :violations [hard] :warnings [soft]
  :confidence c}. :ok? is true iff there are no HARD violations. The registry
  comes from context :registry (default default-registry)."
  [_request context proposal]
  (let [effect     (:effect proposal)
        briefings  (:briefings proposal)
        conf       (:confidence proposal 0.0)
        registry   (or (:registry context) default-registry)
        hard (cond-> []
               (not= :assessment effect)
               (conj {:rule :no-actuation
                      :detail "kouhou only curates; :effect must be :assessment"})
               (catastrophe? briefings)
               (conj {:rule :catastrophe-veto
                      :detail "Rider §2 catastrophe-veto scan hit — never published"})
               (commercial? briefings)
               (conj {:rule :commercial-content
                      :detail "ad / sponsored markers — off-mission for public-interest briefing"})
               (some #(str/blank? (:source-url %)) briefings)
               (conj {:rule :no-provenance
                      :detail "a briefing needs a source-url (provenance)"})
               (some #(unregistered? registry %) briefings)
               (conj {:rule :source-not-in-registry
                      :detail "source host not in the public-sector/public-interest registry"}))
        soft (cond-> []
               (< conf confidence-floor)
               (conj {:rule :low-confidence
                      :detail (str "confidence " conf " < floor " confidence-floor)}))]
    {:ok? (empty? hard) :violations hard :warnings soft :confidence conf}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t :governor-hold :op (:op request) :source (:source-id request)
   :actor (:actor-id context) :disposition :hold
   :basis (mapv :rule (:violations verdict)) :violations (:violations verdict)})

(defn verdict->disposition
  "Map a PublicInfoGovernor verdict to a base disposition. HARD → :hold, else :commit."
  [verdict]
  (if (:ok? verdict) :commit :hold))
