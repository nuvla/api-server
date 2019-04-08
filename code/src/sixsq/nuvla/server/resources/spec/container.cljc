(ns sixsq.nuvla.server.resources.spec.container
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;; image spec

(s/def ::registry
  (-> (st/spec ::cimi-core/url)
      (assoc :name "registry"
             :json-schema/name "registry"
             :json-schema/type "uri"
             :json-schema/mutable true

             :json-schema/display-name "registry"
             :json-schema/description "registry")))


(s/def ::repository
  (-> (st/spec ::cimi-core/token)
      (assoc :name "repository"
             :json-schema/name "repository"
             :json-schema/type "string"
             :json-schema/mutable true

             :json-schema/display-name "repository"
             :json-schema/description "repository")))


(s/def ::image-name
  (-> (st/spec ::cimi-core/token)
      (assoc :name "image-name"
             :json-schema/name "image-name"
             :json-schema/type "string"
             :json-schema/mutable true

             :json-schema/display-name "image name"
             :json-schema/description "image name")))


(s/def ::tag
  (-> (st/spec ::cimi-core/token)
      (assoc :name "tag"
             :json-schema/name "tag"
             :json-schema/type "string"
             :json-schema/mutable true

             :json-schema/display-name "tag"
             :json-schema/description "tag"
             :json-schema/value-scope {:default "latest"})))


(s/def ::image
  (-> (st/spec (su/only-keys-maps {:req-un [::image-name]
                                   :opt-un [::registry
                                            ::tag
                                            ::repository]}))
      (assoc :name "image"
             :json-schema/name "image"
             :json-schema/type "map"
             :json-schema/mutable true

             :json-schema/display-name "image"
             :json-schema/description "image")))


;; ports spec

(s/def ::protocol
  (-> (st/spec #{"tcp" "udp" "sctp"})
      (assoc :name "image"
             :json-schema/name "protocol"
             :json-schema/type "map"
             :json-schema/mutable true
             :json-schema/display-name "protocol"
             :json-schema/description "protocol"
             :json-schema/value-scope {:values  ["tcp", "udp", "sctp"]
                                       :default "tcp"})))


(s/def ::target-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "target-port"
             :json-schema/name "target-port"
             :json-schema/mutable true
             :json-schema/display-name "target port"
             :json-schema/description "target port")))


(s/def ::published-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "published-port"
             :json-schema/name "published-port"
             :json-schema/mutable true
             :json-schema/display-name "published port"
             :json-schema/description "published port")))


(s/def ::port
  (-> (st/spec (su/only-keys-maps {:req-un [::target-port]
                                   :opt-un [::protocol
                                            ::published-port]}))
      (assoc :name "port"
             :json-schema/name "port"
             :json-schema/type "map"
             :json-schema/mutable true
             :json-schema/display-name "port"
             :json-schema/description "port")))


(s/def ::ports
  (-> (st/spec (s/coll-of ::port :kind vector?))
      (assoc :name "ports"
             :json-schema/name "ports"
             :json-schema/type "array"
             :json-schema/mutable true
             :json-schema/indexed false
             :json-schema/display-name "ports"
             :json-schema/description "list of port")))


;; mounts spec


(s/def ::source
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "source"
             :json-schema/name "source"
             :json-schema/type "string"
             :json-schema/mutable true
             :json-schema/display-name "source"
             :json-schema/description "source")))


(s/def ::target
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "target"
             :json-schema/name "target"
             :json-schema/type "string"
             :json-schema/mutable true
             :json-schema/display-name "target"
             :json-schema/description "target")))


(s/def ::mount-type
  (-> (st/spec #{"bind", "volume"})
      (assoc :name "image"
             :json-schema/name "mount-type"
             :json-schema/type "map"
             :json-schema/mutable true
             :json-schema/display-name "mount type"
             :json-schema/description "mount type"
             :json-schema/value-scope {:values ["bind", "volume"]})))


(s/def ::read-only
  (-> (st/spec boolean?)
      (assoc :name "read-only"
             :json-schema/name "read-only"
             :json-schema/type "string"
             :json-schema/mutable true
             :json-schema/display-name "read only"
             :json-schema/description "read only")))


(s/def ::option-key
  (-> (st/spec ::cimi-core/token)
      (assoc :name "option-key"
             :json-schema/name "option-key"
             :json-schema/type "string"
             :json-schema/mutable true
             :json-schema/display-name "option key"
             :json-schema/description "option key")))


(s/def ::option-value
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "option-value"
             :json-schema/name "option-value"
             :json-schema/type "string"
             :json-schema/mutable true
             :json-schema/display-name "option value"
             :json-schema/description "option value")))


(s/def ::volume-option
  (-> (st/spec (su/only-keys-maps {:req-un [::option-key
                                            ::option-value]}))
      (assoc :name "volume-option"
             :json-schema/name "volume-option"
             :json-schema/type "map"
             :json-schema/mutable true
             :json-schema/display-name "volume option"
             :json-schema/description "volume option")))


(s/def ::volume-options
  (-> (st/spec (s/coll-of ::volume-option :kind vector?))
      (assoc :name "volume-options"
             :json-schema/name "volume-options"
             :json-schema/type "array"
             :json-schema/mutable true
             :json-schema/indexed false
             :json-schema/display-name "volume options"
             :json-schema/description "list of volume option")))



(s/def ::mount
  (-> (st/spec (su/only-keys-maps {:req-un [::mount-type
                                            ::target]
                                   :opt-un [::source
                                            ::read-only
                                            ::volume-options]}))
      (assoc :name "mount"
             :json-schema/name "mount"
             :json-schema/type "map"
             :json-schema/mutable true
             :json-schema/display-name "mount"
             :json-schema/description "mount")))


(s/def ::mounts
  (-> (st/spec (s/coll-of ::mount :kind vector?))
      (assoc :name "mounts"
             :json-schema/name "mounts"
             :json-schema/type "array"
             :json-schema/mutable true
             :json-schema/indexed false
             :json-schema/display-name "mounts"
             :json-schema/description "list of mount")))
