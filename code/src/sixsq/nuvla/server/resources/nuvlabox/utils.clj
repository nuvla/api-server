(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
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
                        :last 1000}
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
                      :last 1000}
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
      (let [acls       (get-nuvlabox-acls nuvlabox-ids)
            edit-acl   (into [] (distinct (apply concat (mapv :edit-acl acls))))
            manage     (distinct (concat (apply concat (mapv :manage acls)) edit-acl))
            view-data  (distinct (concat (apply concat (mapv :view-data acls)) edit-acl))
            acl        {:owners    ["group/nuvla-admin"]
                        :delete    (into [] (concat ["group/nuvla-admin"] nuvlabox-ids))
                        :edit-acl  ["group/nuvla-admin"]
                        :manage    (into [] manage)
                        :view-data (into [] view-data)
                        :edit-data edit-acl
                        :view-acl  edit-acl}]
        (assoc resource :acl acl)))))


(defn complete-cluster-details
  [action nb-workers nb-managers {body :body :as request}]
  (let [dyn-body    (apply assoc body
                      (apply concat
                        (filter second
                          (partition 2 [:nuvlabox-workers nb-workers
                                        :nuvlabox-managers nb-managers]))))
        new-body    (set-nuvlabox-cluster-acls dyn-body)]
    (action (assoc request :body new-body))))


(defn has-capability?
  [capability {:keys [capabilities] :as _nuvlabox}]
  (contains? (set capabilities) capability))

(def has-pull-support? (partial has-capability? "NUVLA_JOB_PULL"))

(defn get-execution-mode
  [nuvlabox]
  (if (has-pull-support? nuvlabox) "pull" "push"))

(defn limit-string-size
  [limit s]
  (cond-> s
    (> (count s) limit) (subs 0 limit)))