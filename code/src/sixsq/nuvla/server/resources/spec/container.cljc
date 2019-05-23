(ns sixsq.nuvla.server.resources.spec.container
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
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

(def env-var-regex #"^[A-Z_]+$")


(def reserved-env-var-regex #"NUVLA_.*")


(s/def ::name
  (-> (st/spec (s/and string? #(re-matches env-var-regex %) #(not (re-matches reserved-env-var-regex %))))
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
             :json-schema "boolean"
             :json-schema/description "value required? (default false)")))


(s/def ::environmental-variable
  (-> (st/spec (su/only-keys :req-un [::name]
                             :opt-un [::description ::required ::value]))
      (assoc :name "environmental-variable"
             :json-schema/type "map"
             :json-schema/description "environmental variable name, description, required flag, and value")))


(s/def ::environmental-variables
  (-> (st/spec (s/coll-of ::environmental-variable :kind vector? :min-count 1))
      (assoc :name "environmental-variables"
             :json-schema/type "array"
             :json-schema/display-name "environmental variables"
             :json-schema/description "list of environmental variable")))


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
