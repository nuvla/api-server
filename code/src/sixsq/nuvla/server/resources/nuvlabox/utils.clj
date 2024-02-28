(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.data.csv :as csv]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.job.utils :as job-utils]
    [sixsq.nuvla.server.resources.ts-nuvlaedge-telemetry :as ts-nuvlaedge-telemetry]
    [sixsq.nuvla.server.resources.ts-nuvlaedge-availability :as ts-nuvlaedge-availability]
    [sixsq.nuvla.server.util.kafka-crud :as kafka-crud]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as t]
    [sixsq.nuvla.server.util.time :as time])
  (:import (java.io StringWriter)
           (java.text DecimalFormat DecimalFormatSymbols)
           (java.util Locale)))

(def ^:const state-new "NEW")
(def ^:const state-activated "ACTIVATED")
(def ^:const state-commissioned "COMMISSIONED")
(def ^:const state-decommissioning "DECOMMISSIONING")
(def ^:const state-decommissioned "DECOMMISSIONED")
(def ^:const state-suspended "SUSPENDED")
(def ^:const state-error "ERROR")

(def ^:const capability-job-pull "NUVLA_JOB_PULL")
(def ^:const capability-heartbeat "NUVLA_HEARTBEAT")

(def ^:const action-commission "commission")
(def ^:const action-heartbeat "heartbeat")
(def ^:const action-set-offline "set-offline")

(def ^:const default-refresh-interval 60)
(def ^:const default-heartbeat-interval 20)

(defn is-version-before-2?
  [nuvlabox]
  (< (:version nuvlabox) 2))

(defn has-capability?
  [capability {:keys [capabilities] :as _nuvlabox}]
  (contains? (set capabilities) capability))

(def has-job-pull-support? (partial has-capability? capability-job-pull))

(def has-heartbeat-support? (partial has-capability? capability-heartbeat))

(def is-state-commissioned? (partial u/is-state? state-commissioned))

(def can-delete? (partial u/is-state-within?
                          #{state-new
                            state-decommissioned
                            state-error}))

(def can-activate? (partial u/is-state? state-new))

(def can-commission? (partial u/is-state-within? #{state-activated
                                                   state-commissioned}))

(def can-decommission? (partial u/is-state-besides? #{state-new
                                                      state-decommissioned}))

(defn can-check-api?
  [nuvlabox]
  (and (is-state-commissioned? nuvlabox)
       (is-version-before-2? nuvlabox)))

(def can-add-ssh-key? is-state-commissioned?)

(def can-revoke-ssh-key? is-state-commissioned?)

(def can-update-nuvlabox? is-state-commissioned?)

(def state-not-in-decommissioned-decommissioning-suspended?
  (partial u/is-state-besides?
           #{state-decommissioned state-decommissioning state-suspended}))

(defn can-cluster-nuvlabox?
  [nuvlabox]
  (and (is-state-commissioned? nuvlabox)
       (not (is-version-before-2? nuvlabox))))

(def can-reboot? is-state-commissioned?)

(def is-state-not-in-new-decommissioned-suspended?
  (partial u/is-state-besides? #{state-new state-decommissioned state-suspended}))

(def can-assemble-playbooks? is-state-not-in-new-decommissioned-suspended?)

(def can-enable-emergency-playbooks?
  is-state-not-in-new-decommissioned-suspended?)

(defn can-enable-host-level-management?
  [nuvlabox]
  (and (not (u/is-state? state-suspended nuvlabox))
       (nil? (:host-level-management-api-key nuvlabox))))

(defn can-disable-host-level-management?
  [nuvlabox]
  (some? (:host-level-management-api-key nuvlabox)))

(def can-create-log? state-not-in-decommissioned-decommissioning-suspended?)

(def can-generate-new-api-key?
  state-not-in-decommissioned-decommissioning-suspended?)

(def can-unsuspend? (partial u/is-state? state-suspended))

(defn can-heartbeat?
  [nuvlabox]
  (is-state-commissioned? nuvlabox))

(defn can-set-offline?
  [nuvlabox]
  (u/is-state-within? #{state-commissioned state-suspended} nuvlabox))

(defn throw-nuvlabox-is-suspended
  [{:keys [id] :as nuvlabox}]
  (if (u/is-state? state-suspended nuvlabox)
    (throw (r/ex-response
             (str (format "Call on %s was rejected" id)
                  " because Nuvlabox is in suspended state!")
             409 id))
    nuvlabox))

(defn throw-parent-nuvlabox-is-suspended
  ([{:keys [parent] :as resource}]
   (throw-parent-nuvlabox-is-suspended
     resource (crud/retrieve-by-id-as-admin parent)))
  ([resource nuvlabox]
   (throw-nuvlabox-is-suspended nuvlabox)
   resource))

(defn set-acl-nuvlabox-view-only
  ([nuvlabox-acl]
   (set-acl-nuvlabox-view-only nuvlabox-acl {:owners ["group/nuvla-admin"]}))
  ([nuvlabox-acl current-acl]
   (merge
     (select-keys nuvlabox-acl [:view-acl :view-data :view-meta])
     current-acl)))


(defn short-nb-id
  [nuvlabox-id]
  (when-let [short-id (some-> nuvlabox-id
                              u/id->uuid
                              (str/split #"-")
                              first)]
    short-id))


(defn format-nb-name
  [name id-or-short-id]
  (or name id-or-short-id))


(defn get-matching-nuvlaboxes
  [node-ids]
  (if (seq node-ids)
    (->> {:params      {:resource-name "nuvlabox-status"}
          :cimi-params {:filter (->> node-ids
                                     (u/filter-eq-vals "node-id")
                                     parser/parse-cimi-filter)
                        :select ["parent"]
                        :last   1000}
          :nuvla/authn auth/internal-identity}
         crud/query
         :body
         :resources
         (map :parent)
         (remove nil?)
         (into []))
    []))


(defn get-nuvlabox-acls
  [ids]
  (->> {:params      {:resource-name "nuvlabox"}
        :cimi-params {:filter (->> ids
                                   (u/filter-eq-vals "id")
                                   parser/parse-cimi-filter)
                      :select ["acl"]
                      :last   1000}
        :nuvla/authn auth/internal-identity}
       crud/query
       :body
       :resources
       (mapv :acl)))


(defn set-nuvlabox-cluster-acls
  [resource]
  (when-let [nuvlabox-ids (:nuvlabox-managers resource)]
    (if (empty? nuvlabox-ids)
      (throw (r/ex-bad-request "NuvlaBox clusters must have at least one NuvlaBox"))
      (let [acls      (get-nuvlabox-acls nuvlabox-ids)
            edit-acl  (into [] (distinct (apply concat (mapv :edit-acl acls))))
            manage    (distinct (concat (apply concat (mapv :manage acls)) edit-acl))
            view-data (distinct (concat (apply concat (mapv :view-data acls)) edit-acl))
            acl       {:owners    ["group/nuvla-admin"]
                       :delete    (into [] (distinct (concat ["group/nuvla-admin"] nuvlabox-ids)))
                       :edit-acl  ["group/nuvla-admin"]
                       :manage    (into [] manage)
                       :view-data (into [] view-data)
                       :edit-data edit-acl
                       :view-acl  edit-acl}]
        (assoc resource :acl acl)))))


(defn complete-cluster-details
  [action nb-workers nb-managers status-notes {body :body :as request}]
  (let [dyn-body (apply assoc body
                        (apply concat
                               (filter second
                                       (partition 2 [:nuvlabox-workers nb-workers
                                                     :nuvlabox-managers nb-managers]))))
        body-acl (set-nuvlabox-cluster-acls dyn-body)
        new-body (cond-> body-acl
                         (not-empty status-notes) (assoc :status-notes status-notes))]
    (action (assoc request :body new-body))))

(defn get-execution-mode
  [nuvlabox]
  (if (has-job-pull-support? nuvlabox) "pull" "push"))

(defn get-playbooks
  ([nuvlabox-id] (get-playbooks nuvlabox-id "MANAGEMENT"))
  ([nuvlabox-id type]
   (if-not (or (empty? nuvlabox-id) (empty? type))
     (-> {:params      {:resource-name "nuvlabox-playbook"}
          :cimi-params {:filter (parser/parse-cimi-filter
                                  (format "enabled='true' and parent='%s' and type='%s'" nuvlabox-id type))
                        :select ["run", "id"]
                        :last   1000}
          :nuvla/authn auth/internal-identity}
         crud/query
         :body
         :resources)
     [])))


(defn get-nuvlabox-playbook-filename
  [id]
  (str "/tmp/nuvlabox-playbook-" (last (str/split id #"/"))))


(defn get-nuvlabox-playbook-output-filename
  [id]
  (str (get-nuvlabox-playbook-filename (last (str/split id #"/"))) ".output"))


(defn wrap-playbook-run
  [playbook]
  (let [id                     (last (str/split (:id playbook) #"/"))
        nuvlabox-playbook-file (get-nuvlabox-playbook-filename id)
        nuvlabox-playbook-out  (get-nuvlabox-playbook-output-filename id)]
    (str
      "cat > " nuvlabox-playbook-file " <<'EOF'\n"
      (:run playbook)
      "\nEOF\n"
      "echo '' > " nuvlabox-playbook-out
      "\nsh " nuvlabox-playbook-file
      " 2>&1 | while IFS= read -r line; do printf '[%s] %s\\n' \"$(date '+%Y-%m-%d %H:%M:%S')\" \"$line\" >> "
      nuvlabox-playbook-out "; done || true")))


(def ^:const nuvla-default-api-endpoint (str "https://nuvla.io/api/"))


(def ^:const nuvla-login-script (str "curl -X POST \"${NUVLA_API_ENDPOINT:-" nuvla-default-api-endpoint "}session\" "
                                     "-H content-type:application/json "
                                     "-c /tmp/nuvla-cookie "
                                     "-d \"{\\\"template\\\": {\\\"href\\\": \\\"session-template/api-key\\\",\\\"key\\\": \\\"$NUVLABOX_API_KEY\\\", \\\"secret\\\": \\\"$NUVLABOX_API_SECRET\\\"}}\""))


(defn wrap-and-pipe-playbooks
  [playbooks]
  (if (empty? playbooks)
    ""
    (let [wrapped-runs      (map (fn [playbook] (wrap-playbook-run playbook)) playbooks)
          exec-wrapped-runs (str/join "\n#-- end of playbook --#\n" wrapped-runs)
          save-outputs      (map (fn [playbook]
                                   (str "curl -X POST \"${NUVLA_API_ENDPOINT:-" nuvla-default-api-endpoint "}" (:id playbook) "/save-output\" "
                                        "-H content-type:application/json "
                                        "-b /tmp/nuvla-cookie "
                                        " -d \"{\\\"output\\\": \\\"$(cat "
                                        (get-nuvlabox-playbook-output-filename (:id playbook)) " | sed -e 's/\"/\\\\\"/g' )\\\"}\""))
                                 playbooks)]
      (str "#!/bin/sh\n\n"
           exec-wrapped-runs
           "\n\n"
           nuvla-login-script
           "\n\n"
           (str/join "\n" save-outputs)
           "\n"))))


(defn compose-cronjob
  [credential-api-key nuvlabox-id base-uri]
  (str "0 * * * * export NUVLABOX_API_KEY="
       (:api-key credential-api-key)
       " NUVLABOX_API_SECRET="
       (:secret-key credential-api-key)
       " NUVLA_API_ENDPOINT=" base-uri " && "
       nuvla-login-script
       " && curl -X POST \"${NUVLA_API_ENDPOINT:-" nuvla-default-api-endpoint "}" nuvlabox-id "/assemble-playbooks\" -b /tmp/nuvla-cookie | sh -"))


(defn limit-string-size
  [limit s]
  (cond-> s
          (> (count s) limit) (subs 0 limit)))


(defn throw-when-payment-required
  [request]
  (if (or (nil? config-nuvla/*stripe-api-key*)
          (a/is-admin? (auth/current-authentication request))
          (let [active-claim (auth/current-active-claim request)
                subs-status  (:status (payment/active-claim->subscription
                                        active-claim))]
            (#{"active" "past_due" "trialing"} subs-status)))
    request
    (payment/throw-payment-required)))


(defn throw-value-should-be-bigger
  [request k min-value]
  (let [v (get-in request [:body k])]
    (if (or (nil? v)
            (a/is-admin-request? request)
            (>= v min-value))
      request
      (throw (r/ex-response
               (str (name k) " should not be less than " min-value "!")
               400)))))

(defn throw-refresh-interval-should-be-bigger
  [request nuvlabox]
  (if (and nuvlabox (not (has-heartbeat-support? nuvlabox)))
    (throw-value-should-be-bigger request :refresh-interval 30)
    (throw-value-should-be-bigger request :refresh-interval 60)))

(defn throw-heartbeat-interval-should-be-bigger
  [request]
  (throw-value-should-be-bigger request :heartbeat-interval 10))

(defn throw-vpn-server-id-should-be-vpn
  [{{:keys [vpn-server-id]} :body :as request}]
  (if vpn-server-id
    (let [vpn-service (vpn-utils/get-service vpn-server-id)]
      (or (vpn-utils/check-service-subtype vpn-service)
          request))
    request))

(defn compute-next-report
  [interval-in-seconds tolerance-fn]
  (some-> interval-in-seconds
          tolerance-fn
          (time/from-now :seconds)
          time/to-str))

(defn status-online-attributes
  [online online-new time-interval]
  (cond-> {:online  online-new
           :updated (time/now-str)}
          online-new (assoc :last-heartbeat (time/now-str)
                            :next-heartbeat (compute-next-report
                                              time-interval
                                              #(-> % (* 2) (+ 10))))
          (some? online)
          (assoc :online-prev online)))

(declare bulk-insert-metrics)
(declare track-availability)

(defn set-online!
  [{:keys [id nuvlabox-status heartbeat-interval online]
    :or   {heartbeat-interval default-heartbeat-interval}
    :as   nuvlabox} online-new]
  (let [nb-status (status-online-attributes
                    online online-new heartbeat-interval)]
    (r/throw-response-not-200
      (db/scripted-edit id {:refresh false
                            :body    {:doc {:online             online-new
                                            :heartbeat-interval heartbeat-interval}}}))
    (r/throw-response-not-200
      (db/scripted-edit nuvlabox-status {:refresh false
                                         :body    {:doc nb-status}}))
    (kafka-crud/publish-on-edit
      "nuvlabox-status"
      (r/json-response (assoc nb-status :id nuvlabox-status
                                        :parent id
                                        :acl (:acl nuvlabox))))
    (track-availability (assoc nb-status :parent id) false)
    nuvlabox))

(defn get-jobs
  [nb-id]
  (->> {:params      {:resource-name "job"}
        :cimi-params {:filter  (cimi-params-impl/cimi-filter
                                 {:filter (str "execution-mode='pull' and "
                                               "state!="
                                               (vec job-utils/final-states))})
                      :select  ["id"]
                      :orderby [["created" :asc]]}
        :nuvla/authn {:user-id      nb-id
                      :active-claim nb-id
                      :claims       #{nb-id "group/nuvla-user" "group/nuvla-anon"}}}
       crud/query
       :body
       :resources
       (mapv :id)))

(defn build-response
  [{:keys [id updated] :as _nuvlabox}]
  {:doc-last-updated updated
   :jobs             (get-jobs id)})

(defn nuvlabox-request?
  [request]
  (str/starts-with? (auth/current-active-claim request) "nuvlabox/"))

(defn legacy-heartbeat
  [nuvlabox-status request {:keys [online refresh-interval] :as nuvlabox}]
  (if (and (nuvlabox-request? request)
           (not (has-heartbeat-support? nuvlabox)))
    (merge nuvlabox-status
           (status-online-attributes online true refresh-interval))
    nuvlabox-status))

(defn get-service
  "Searches for an existing infrastructure-service of the given subtype and
   linked to the given infrastructure-service-group. If found, the identifier
   is returned."
  [subtype isg-id]
  (let [filter  (format "subtype='%s' and parent='%s'" subtype isg-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin infra-service/resource-type options)
        second
        first
        :id)))

;; ts-nuvlaedge utils

(defn latest-availability-status
  ([nuvlaedge-id]
   (latest-availability-status nuvlaedge-id nil))
  ([nuvlaedge-id before-timestamp]
   (->> {:cimi-params {:filter  (cimi-params-impl/cimi-filter
                                  {:filter (cond-> (str "nuvlaedge-id='" nuvlaedge-id "'")
                                                   before-timestamp
                                                   (str " and @timestamp<'" (time/to-str before-timestamp) "'"))
                                   :last   1})
                       :select  ["@timestamp" "online"]
                       :orderby [["@timestamp" :desc]]}
         ;; sending an empty :tsds-aggregation to avoid acl checks. TODO: find a cleaner way
         :params      {:tsds-aggregation "{}"}}
        (crud/query-as-admin ts-nuvlaedge-availability/resource-type)
        second
        first)))

(defn build-aggregations-clause
  [{:keys [from to ts-interval aggregations] group-by-field :group-by}]
  (let [tsds-aggregations {:tsds-stats
                           {:date_histogram
                            {:field           "@timestamp"
                             :fixed_interval  ts-interval
                             :min_doc_count   0
                             :extended_bounds {:min (time/to-str from)
                                               :max (time/to-str to)}}
                            :aggregations (or aggregations {})}}]
    (if group-by-field
      {:aggregations
       {:by-field
        {:terms        {:field group-by-field}
         :aggregations tsds-aggregations}}}
      {:aggregations tsds-aggregations})))

(defn build-ts-query [{:keys [last nuvlaedge-ids from to additional-filters] :as options}]
  (let [nuvlabox-id-filter (str "nuvlaedge-id=[" (str/join " " (map #(str "'" % "'")
                                                                    nuvlaedge-ids))
                                "]")
        time-range-filter  (str "@timestamp>'" (time/to-str from) "'"
                                " and "
                                "@timestamp<'" (time/to-str to) "'")
        aggregation-clause (build-aggregations-clause options)]
    (cond->
      {:cimi-params {:last (or last 0)
                     :filter
                     (parser/parse-cimi-filter
                       (str "("
                            (apply str
                                   (interpose " and "
                                              (into [nuvlabox-id-filter
                                                     time-range-filter]
                                                    additional-filters)))
                            ")"))}}
      aggregation-clause
      (assoc :params {:tsds-aggregation (json/write-str aggregation-clause)}))))

(defn build-metrics-query [{:keys [metric] :as options}]
  (build-ts-query (assoc options :additional-filters [(str "metric='" metric "'")])))

(defn build-availability-query [options]
  ;; return up to 10000 availability state updates
  (build-ts-query (assoc options :last 10000)))

(defn ->metrics-resp
  [{:keys [mode nuvlaedge-ids aggregations] group-by-field :group-by} resp]
  (let [ts-data    (fn [tsds-stats]
                     (map
                       (fn [{:keys [key_as_string doc_count] :as bucket}]
                         {:timestamp    key_as_string
                          :doc-count    doc_count
                          :aggregations (->> (keys aggregations)
                                             (select-keys bucket)
                                             #_(map (fn [[k agg-bucket]] [k (agg-resp agg-bucket)]))
                                             #_(into {}))})
                       (:buckets tsds-stats)))
        dimensions (case mode
                     :single-edge-query
                     {:nuvlaedge-id (first nuvlaedge-ids)}
                     :multi-edge-query
                     {:nuvlaedge-count (count nuvlaedge-ids)})
        hits       (second resp)]
    (if group-by-field
      (for [{:keys [key tsds-stats]} (get-in resp [0 :aggregations :by-field :buckets])]
        (cond->
          {:dimensions (assoc dimensions group-by-field key)
           :ts-data    (ts-data tsds-stats)}
          (seq hits) (assoc :hits hits)))
      [(cond->
         {:dimensions dimensions
          :ts-data    (ts-data (get-in resp [0 :aggregations :tsds-stats]))}
         (seq hits) (assoc :hits hits))])))

(defn query-metrics
  [options]
  (->> (build-metrics-query options)
       (crud/query-as-admin ts-nuvlaedge-telemetry/resource-type)
       (->metrics-resp options)))

(defn query-availability
  [options]
  (->> (build-availability-query options)
       (crud/query-as-admin ts-nuvlaedge-availability/resource-type)
       (->metrics-resp options)))

(defn metrics-data->csv [dimension-keys aggregation-keys response]
  (with-open [writer (StringWriter.)]
    ;; write cav header
    (csv/write-csv writer [(concat (map name dimension-keys)
                                   ["timestamp" "doc-count"]
                                   (map name aggregation-keys))])
    ;; write csv data
    (let [df (DecimalFormat. "0.####" (DecimalFormatSymbols. Locale/US))]
      (csv/write-csv writer
                     (for [{:keys [dimensions ts-data]} response
                           {:keys [timestamp doc-count aggregations]} ts-data]
                       (concat (map dimensions dimension-keys)
                               [timestamp doc-count]
                               (map (fn [agg-key]
                                      (let [v (get-in aggregations [agg-key :value])]
                                        (if (float? v)
                                          ;; format floats with 4 decimal and dot separator
                                          (.format df v)
                                          v)))
                                    aggregation-keys)))))
    (.toString writer)))

(defmulti nuvlabox-status->metric-data (fn [_ _nb metric _] metric))

(defmethod nuvlabox-status->metric-data :default
  [{:keys [resources]} _nb metric _from-telemetry]
  (when-let [metric-data (get resources metric)]
    [{metric metric-data}]))

(defmethod nuvlabox-status->metric-data :cpu
  [{{:keys [cpu]} :resources} _nb _metric _from-telemetry]
  (when cpu
    [{:cpu (select-keys cpu
                        [:capacity
                         :load
                         :load-1
                         :load-5
                         :context-switches
                         :interrupts
                         :software-interrupts
                         :system-calls])}]))

(defmethod nuvlabox-status->metric-data :ram
  [{{:keys [ram]} :resources} _nb _metric _from-telemetry]
  (when ram
    [{:ram (select-keys ram [:capacity :used])}]))

(defmethod nuvlabox-status->metric-data :disk
  [{{:keys [disks]} :resources} _nb _metric _from-telemetry]
  (when (seq disks)
    (mapv (fn [data] {:disk (select-keys data [:device :capacity :used])}) disks)))

(defmethod nuvlabox-status->metric-data :network
  [{{:keys [net-stats]} :resources} _nb _metric _from-telemetry]
  (when (seq net-stats)
    (mapv (fn [data] {:network (select-keys data [:interface :bytes-transmitted :bytes-received])}) net-stats)))

(defmethod nuvlabox-status->metric-data :power-consumption
  [{{:keys [power-consumption]} :resources} _nb _metric _from-telemetry]
  (when (seq power-consumption)
    (mapv (fn [data] {:power-consumption (select-keys data [:metric-name :energy-consumption :unit])}) power-consumption)))

(defn nuvlabox-status->bulk-insert-metrics-request-body
  [{:keys [parent current-time] :as nuvlabox-status} from-telemetry]
  (let [nb (crud/retrieve-by-id-as-admin parent)]
    (->> [:cpu :ram :disk :network :power-consumption]
         (map (fn [metric]
                (->> (nuvlabox-status->metric-data nuvlabox-status nb metric from-telemetry)
                     (map #(merge
                             {:nuvlaedge-id parent
                              :metric       (name metric)
                              :timestamp    current-time}
                             %)))))
         (apply concat))))

(defn nuvlabox-status->bulk-insert-metrics-request
  [nb-status from-telemetry]
  (let [body (->> (nuvlabox-status->bulk-insert-metrics-request-body nb-status from-telemetry)
                  ;; only retain metrics where a timestamp is defined
                  (filter :timestamp))]
    (when (seq body)
      {:headers     {"bulk" true}
       :params      {:resource-name ts-nuvlaedge-telemetry/resource-type
                     :action        "bulk-insert"}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn bulk-insert-metrics
  [nb-status from-telemetry]
  (try
    (some-> nb-status
            (nuvlabox-status->bulk-insert-metrics-request from-telemetry)
            (crud/bulk-action))
    (catch Exception ex
      (log/error "An error occurred inserting metrics: " ex))))

(defn nuvlabox-status->insert-availability-request-body
  [{:keys [parent online] :as _nuvlabox-status} from-telemetry]
  (when (some? online)
    (let [nb (crud/retrieve-by-id-as-admin parent)]
      ;; when online status is sent via heartbeats, do not store those sent via telemetry
      (when (or (not (has-heartbeat-support? nb)) (not from-telemetry))
        (let [now    (time/now)
              latest (latest-availability-status (:id nb) now)]
          ;; when availability status has changed, or no availability data was recorded for the day yet
          (when (or (not= (:online latest)
                          (if online 1 0))
                    (not= (some-> (:timestamp latest) t/date-from-str (.toLocalDate))
                          (.toLocalDate now)))
            {:nuvlaedge-id parent
             :timestamp    (time/to-str now)
             :online       (if (true? online) 1 0)}))))))

(defn nuvlabox-status->insert-availability-request
  [nb-status from-telemetry]
  (let [body (nuvlabox-status->insert-availability-request-body nb-status from-telemetry)]
    (when body
      {:params      {:resource-name ts-nuvlaedge-availability/resource-type}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn track-availability
  [nb-status from-telemetry]
  (try
    (some-> nb-status
            (nuvlabox-status->insert-availability-request from-telemetry)
            (crud/add))
    (catch Exception ex
      (log/error "An error occurred updating availability: " ex))))
