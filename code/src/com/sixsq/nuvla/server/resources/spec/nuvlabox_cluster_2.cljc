(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-cluster-2
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-cluster :as nb-cluster]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::orchestrator
  (-> (st/spec #{"swarm" "kubernetes"})
    (assoc :name "orchestrator"
           :json-schema/type "string"
           :json-schema/value-scope {:values ["swarm" "kubernetes"]}
           :json-schema/description "Container orchestration being used, if part of a cluster"

           :json-schema/order 11)))

(s/def ::managers
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "managers"
           :json-schema/description "List of Node IDs in the cluster, corresponding to the managers only"

           :json-schema/order 12)))

(s/def ::nuvlabox-managers
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "nuvlabox-managers"
           :json-schema/description "NuvlaBox IDs of the managers"

           :json-schema/order 13)))

(s/def ::workers
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 0 :kind vector?))
    (assoc :name "workers"
           :json-schema/description "List of Node IDs in the cluster, corresponding to the workers only"

           :json-schema/order 14)))

(s/def ::nuvlabox-workers
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 0 :kind vector?))
    (assoc :name "nuvlabox-workers"
           :json-schema/description "NuvlaBox IDs of the workers"

           :json-schema/order 15)))

(s/def ::cluster-id
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "cluster-id"
           :json-schema/description "ID of the cluster"

           :json-schema/order 16)))

(s/def ::status-notes
  (-> (st/spec (s/coll-of string? :kind vector?))
    (assoc :name "status-notes"
      :json-schema/description "List of notes related with the status of the cluster"

      :json-schema/order 83)))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
    nb-cluster/attributes
    {:req-un [::cluster-id
              ::orchestrator
              ::managers]
     :opt-un [::workers
              ::nuvlabox-workers
              ::nuvlabox-managers
              ::status-notes]}))
