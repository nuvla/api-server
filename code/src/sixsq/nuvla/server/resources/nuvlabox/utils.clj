(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [clojure.pprint :refer [pprint]]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))


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
    (mapv :parent)))


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


