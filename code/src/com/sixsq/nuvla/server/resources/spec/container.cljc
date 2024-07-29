(ns com.sixsq.nuvla.server.resources.spec.container
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;; image spec

(s/def ::registry
  (-> (st/spec ::core/url)
      (assoc :name "registry"
             :json-schema/description "registry")))


(s/def ::repository
  (-> (st/spec ::core/token)
      (assoc :name "repository"
             :json-schema/description "repository")))


(s/def ::image-name
  (-> (st/spec ::core/token)
      (assoc :name "image-name"
             :json-schema/display-name "image name"
             :json-schema/description "image name")))


(s/def ::tag
  (-> (st/spec ::core/token)
      (assoc :name "tag"
             :json-schema/description "tag"

             ;; FIXME: This value-scope definition is not legal.
             ;:json-schema/value-scope {:default "latest"}
             )))


(s/def ::image
  (-> (st/spec (su/only-keys-maps {:req-un [::image-name]
                                   :opt-un [::registry
                                            ::tag
                                            ::repository]}))
      (assoc :name "image"
             :json-schema/type "map"
             :json-schema/description "image")))


(def ^:const infrastructure-service-id-regex
  #"^infrastructure-service/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn infrastructure-service-id? [s] (re-matches infrastructure-service-id-regex s))

(s/def ::infrastructure-service-id (s/and string? infrastructure-service-id?))

(s/def ::private-registries
  (-> (st/spec (s/coll-of ::infrastructure-service-id :min-count 1 :kind vector? :distinct true))
      (assoc :name "private-registries"
             :json-schema/type "array"
             :json-schema/indexed false

             :json-schema/display-name "private registries"
             :json-schema/description "list of used infrastructure service of subtype registry"
             :json-schema/order 39)))


;; ports spec

(s/def ::protocol
  (-> (st/spec #{"tcp" "udp" "sctp"})
      (assoc :name "protocol"
             :json-schema/type "string"
             :json-schema/description "protocol"

             :json-schema/value-scope {:values  ["tcp" "udp" "sctp"]
                                       :default "tcp"})))


(s/def ::target-port
  (-> (st/spec ::core/port)
      (assoc :name "target-port"
             :json-schema/display-name "target port"
             :json-schema/description "target port")))


(s/def ::published-port
  (-> (st/spec ::core/port)
      (assoc :name "published-port"
             :json-schema/display-name "published port"
             :json-schema/description "published port")))


(s/def ::port
  (-> (st/spec (su/only-keys-maps {:req-un [::target-port]
                                   :opt-un [::protocol
                                            ::published-port]}))
      (assoc :name "port"
             :json-schema/type "map"
             :json-schema/description "port")))


(s/def ::ports
  (-> (st/spec (s/coll-of ::port :kind vector?))
      (assoc :name "ports"
             :json-schema/type "array"
             :json-schema/description "list of port"

             :json-schema/indexed false)))


;;
;; environmental variables
;;

(def env-var-regex #"^[a-zA-Z_]+[a-zA-Z0-9_]*$")


(def reserved-env-var-regex #"NUVLA_.*")


(s/def ::name
  (-> (st/spec (s/and string? #(re-matches env-var-regex %)
                      #(not (re-matches reserved-env-var-regex %))))
      (assoc :name "name"
             :json-schema/type "string"
             :json-schema/description "parameter name")))


(s/def ::description
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "description"
             :json-schema/description "parameter description")))


(s/def ::value
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "value"
             :json-schema/description "parameter value")))


(s/def ::required
  (-> (st/spec boolean?)
      (assoc :name "required"
             :json-schema/type "boolean"
             :json-schema/description "value required? (default false)")))


(s/def ::environmental-variable
  (-> (st/spec (su/only-keys :req-un [::name]
                             :opt-un [::description ::required ::value]))
      (assoc :name "environmental-variable"
             :json-schema/type "map"
             :json-schema/description
             "environmental variable name, description, required flag, and value")))


(s/def ::environmental-variables
  (-> (st/spec (s/coll-of ::environmental-variable :kind vector? :min-count 1))
      (assoc :name "environmental-variables"
             :json-schema/type "array"
             :json-schema/display-name "environmental variables"
             :json-schema/description "list of environmental variable")))


;;
;; resource constraints
;;

(s/def ::memory
  (-> (st/spec pos-int?)
      (assoc :name "memory"
             :json-schema/type "integer"
             :json-schema/description "maximum memory in MiB")))


(s/def ::cpus
  (-> (st/spec (s/and number? pos?))
      (assoc :name "cpus"
             :json-schema/type "double"
             :json-schema/display-name "CPUs"
             :json-schema/description "allocated virtual CPUs")))

;;
;; restart policy
;;

(s/def ::condition
  (-> (st/spec #{"none", "on-failure", "any"})
      (assoc :name "condition"
             :json-schema/type "string"
             :json-schema/value-scope {:values  ["none", "on-failure", "any"]}
             :json-schema/description "restart condition (none, on-failure, any)")))


(s/def ::delay
  (-> (st/spec nat-int?)
      (assoc :name "delay"
             :json-schema/type "integer"
             :json-schema/description "delay between restarts (seconds)")))


(s/def ::max-attempts
  (-> (st/spec nat-int?)
      (assoc :name "max-attempts"
             :json-schema/type "integer"
             :json-schema/display-name "max. attempts"
             :json-schema/description "maximum number of restart attempts")))


(s/def ::window
  (-> (st/spec nat-int?)
      (assoc :name "window"
             :json-schema/type "integer"
             :json-schema/description "time window used to evaluate restart policy (seconds)")))


(s/def ::restart-policy
  (-> (st/spec (su/only-keys :req-un [::condition]
                             :opt-un [::delay ::max-attempts ::window]))
      (assoc :name "restart-policy"
             :json-schema/type "map"
             :json-schema/display-name "restart policy"
             :json-schema/description "Docker restart policy for the container")))


;; mounts spec


(s/def ::source
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "source"
             :json-schema/description "source directory to be mounted into container")))


(s/def ::target
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "target"
             :json-schema/description "target directory in container for mount")))


(s/def ::mount-type
  (-> (st/spec #{"volume" "bind" "tmpfs"})
      (assoc :name "mount-type"
             :json-schema/type "string"
             :json-schema/description "mount type"

             :json-schema/value-scope {:values ["volume" "bind" "tmpfs"]})))


(s/def ::read-only
  (-> (st/spec boolean?)
      (assoc :name "read-only"
             :json-schema/type "boolean"
             :json-schema/display-name "read only"
             :json-schema/description "read only")))


(s/def ::target
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "target"
             :json-schema/description "target directory in container for mount")))


(s/def ::volume-options
  (-> (st/spec (s/map-of keyword? string?))
      (assoc :name "volume-options"
             :json-schema/type "map"
             :json-schema/description "volume options to mount data on container"

             :json-schema/indexed false)))


(s/def ::mount
  (-> (st/spec (su/only-keys-maps {:req-un [::mount-type
                                            ::target]
                                   :opt-un [::source
                                            ::read-only
                                            ::volume-options]}))
      (assoc :name "mount"
             :json-schema/type "map"
             :json-schema/description "options to mount data on container")))


(s/def ::mounts
  (-> (st/spec (s/coll-of ::mount :kind vector?))
      (assoc :name "mounts"
             :json-schema/type "array"
             :json-schema/description "list of mounts"

             :json-schema/indexed false)))
