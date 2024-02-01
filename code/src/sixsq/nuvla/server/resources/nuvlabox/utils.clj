(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
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
    [sixsq.nuvla.server.resources.nuvlabox.ts-nuvlaedge-utils :as ts-nuvlaedge-utils]
    [sixsq.nuvla.server.util.kafka-crud :as kafka-crud]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))

(def ^:const state-new "NEW")
(def ^:const state-activated "ACTIVATED")
(def ^:const state-commissioned "COMMISSIONED")
(def ^:const state-decommissioning "DECOMMISSIONING")
(def ^:const state-decommissioned "DECOMMISSIONED")
(def ^:const state-suspended "SUSPENDED")
(def ^:const state-error "ERROR")

(def ^:const capability-job-pull "NUVLA_JOB_PULL")
(def ^:const capability-heartbeat "NUVLA_HEARTBEAT")

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
    (ts-nuvlaedge-utils/bulk-insert-metrics (assoc nb-status :parent id))
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

