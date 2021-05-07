(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-2
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-0 :as nb-status-0]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; This version of the schema is the same as the previous one (0),
;; except that the peripherals attribute has been removed. Use the
;; same attribute definitions to avoid repetition.

(s/def ::host-user-home
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "host-user-home"
           :json-schema/description "Home directory, on the host, of the user who installed the NuvlaBox"

           :json-schema/order 77)))

(s/def ::node-id
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "node-id"
           :json-schema/description "Node ID, if it exists (usually assigned when node belongs to a cluster)"

           :json-schema/order 78)))

(s/def ::cluster-id
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "cluster-id"
           :json-schema/description "Cluster ID, if it exists (usually assigned when node is a cluster manager)"

           :json-schema/order 79)))

(s/def ::cluster-node-role
  (-> (st/spec #{"manager" "worker"})
    (assoc :name "cluster-node-role"
           :json-schema/type "string"
           :json-schema/description "Role of the node in the cluster, if any"
           :json-schema/value-scope {:values ["manager" "worker"]}

           :json-schema/order 80)))

(s/def ::status-notes
  (-> (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    (assoc :name "status-notes"
           :json-schema/description "Previously called 'comment', now turned into a list of notes related with the status"

           :json-schema/order 83)))

(s/def ::cluster-nodes
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "cluster-nodes"
           :json-schema/description "List of Node IDs in the cluster"

           :json-schema/order 81)))

(s/def ::cluster-managers
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "cluster-managers"
           :json-schema/description "List of Node IDs in the cluster, corresponding to the managers only"

           :json-schema/order 82)))

(s/def ::orchestrator
  (-> (st/spec #{"swarm" "kubernetes"})
    (assoc :name "orchestrator"
           :json-schema/type "string"
           :json-schema/value-scope {:values ["swarm" "kubernetes"]}
           :json-schema/description "Container orchestration being used, if part of a cluster"

           :json-schema/order 84)))

(s/def ::cluster-join-address
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "cluster-join-address"
           :json-schema/description "If manager, this is the advertised address to be used by other nodes to join"

           :json-schema/order 85)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::nb-status-0/status]
                      :opt-un [::nb-status-0/next-heartbeat
                               ::nb-status-0/current-time
                               ::nb-status-0/resources
                               ::nb-status-0/resources-prev
                               ::nb-status-0/operating-system
                               ::nb-status-0/architecture
                               ::nb-status-0/hostname
                               ::nb-status-0/ip
                               ::nb-status-0/docker-server-version
                               ::nb-status-0/last-boot
                               ::nb-status-0/wifi-password
                               ::nb-status-0/nuvlabox-api-endpoint
                               ::nb-status-0/inferred-location
                               ::nb-status-0/gpio-pins
                               ::nb-status-0/nuvlabox-engine-version
                               ::nb-status-0/docker-plugins
                               ::nb-status-0/vulnerabilities
                               ::nb-status-0/installation-parameters
                               ::nb-status-0/jobs
                               ::nb-status-0/swarm-node-cert-expiry-date
                               ::nb-status-0/online
                               ::nb-status-0/online-prev
                               ::host-user-home
                               ::node-id
                               ::cluster-id
                               ::cluster-node-role
                               ::cluster-nodes
                               ::cluster-managers
                               ::cluster-join-address
                               ::status-notes
                               ::orchestrator
                               ::container-stats]}))

