(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox-log :as nuvlabox-log]
    [sixsq.nuvla.server.util.response :as r]))


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
  (if-not (empty? node-ids)
    (->> {:params      {:resource-name "nuvlabox-status"}
          :cimi-params {:filter (cimi-params-impl/cimi-filter
                                  {:filter (->> node-ids
                                                (map #(str "node-id='" % "'"))
                                                (str/join " or "))})
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
        :cimi-params {:filter (cimi-params-impl/cimi-filter
                                {:filter (->> ids
                                              (map #(str "id='" % "'"))
                                              (str/join " or "))})
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
                       :delete    (into [] (concat ["group/nuvla-admin"] nuvlabox-ids))
                       :edit-acl  ["group/nuvla-admin"]
                       :manage    (into [] manage)
                       :view-data (into [] view-data)
                       :edit-data edit-acl
                       :view-acl  edit-acl}]
        (assoc resource :acl acl)))))


(defn complete-cluster-details
  [action nb-workers nb-managers {body :body :as request}]
  (let [dyn-body (apply assoc body
                        (apply concat
                               (filter second
                                       (partition 2 [:nuvlabox-workers nb-workers
                                                     :nuvlabox-managers nb-managers]))))
        new-body (set-nuvlabox-cluster-acls dyn-body)]
    (action (assoc request :body new-body))))


(defn has-capability?
  [capability {:keys [capabilities] :as _nuvlabox}]
  (contains? (set capabilities) capability))

(def has-pull-support? (partial has-capability? "NUVLA_JOB_PULL"))

(defn get-execution-mode
  [nuvlabox]
  (if (has-pull-support? nuvlabox) "pull" "push"))

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


(def ^:const nuvla-login-script (str "curl -X POST ${NUVLA_ENDPOINT:-https://nuvla.io}/api/session "
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
                                   (str "curl -X POST ${NUVLA_ENDPOINT:-https://nuvla.io}/api/" (:id playbook) "/save-output "
                                        "-H content-type:application/json "
                                        "-b /tmp/nuvla-cookie "
                                        " -d \"{\\\"output\\\": \\\"$(cat "
                                        (get-nuvlabox-playbook-output-filename (:id playbook)) ")\\\"\""))
                                 playbooks)]
      (str "#!/bin/sh\n\n"
           exec-wrapped-runs
           "\n\n"
           nuvla-login-script
           "\n\n"
           (str/join "\n" save-outputs)
           "\n"))))


(defn compose-cronjob
  [credential-api-key nuvlabox-id]
  (str "* 0 * * * export NUVLABOX_API_KEY="
       (:api-key credential-api-key)
       " NUVLABOX_API_SECRET="
       (:secret-key credential-api-key)
       " NUVLA_ENDPOINT=https://nuvla.io && "
       nuvla-login-script
       " && curl -X POST ${NUVLA_ENDPOINT:-https://nuvla.io}/api/nuvlabox/" nuvlabox-id "/assemble-playbooks -b /tmp/nuvla-cookie | sh -"))


(defn limit-string-size
  [limit s]
  (cond-> s
          (> (count s) limit) (subs 0 limit)))


(defn can-create-log?
  [{:keys [state] :as _resource}]
  (contains? #{"ACTIVATED" "COMISSIONED" "DECOMMISSIONING" "ERROR"} state))


(defn create-log
  [{:keys [id] :as _resource} {:keys [body] :as request}]
  (let [session-id (auth/current-session-id request)
        opts       (select-keys body [:since :lines])]
    (nuvlabox-log/create-log id session-id opts)))