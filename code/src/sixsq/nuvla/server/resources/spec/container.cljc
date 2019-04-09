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
             :json-schema/description "registry")))


(s/def ::repository
  (-> (st/spec ::cimi-core/token)
      (assoc :name "repository"
             :json-schema/description "repository")))


(s/def ::image-name
  (-> (st/spec ::cimi-core/token)
      (assoc :name "image-name"
             :json-schema/display-name "image name"
             :json-schema/description "image name")))


(s/def ::tag
  (-> (st/spec ::cimi-core/token)
      (assoc :name "tag"
             :json-schema/description "tag"

             :json-schema/value-scope {:default "latest"})))


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
  (-> (st/spec ::cimi-core/port)
      (assoc :name "target-port"
             :json-schema/display-name "target port"
             :json-schema/description "target port")))


(s/def ::published-port
  (-> (st/spec ::cimi-core/port)
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


;; mounts spec


(s/def ::source
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "source"
             :json-schema/description "source")))


(s/def ::target
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "target"
             :json-schema/description "target")))


(s/def ::mount-type
  (-> (st/spec #{"bind" "volume"})
      (assoc :name "image"
             :json-schema/type "string"
             :json-schema/display-name "mount type"
             :json-schema/description "mount type"

             :json-schema/value-scope {:values ["bind" "volume"]})))


(s/def ::read-only
  (-> (st/spec boolean?)
      (assoc :name "read-only"
             :json-schema/type "boolean"
             :json-schema/display-name "read only"
             :json-schema/description "read only")))


(s/def ::option-key
  (-> (st/spec ::cimi-core/token)
      (assoc :name "option-key"
             :json-schema/display-name "option key"
             :json-schema/description "option key")))


(s/def ::option-value
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "option-value"
             :json-schema/display-name "option value"
             :json-schema/description "option value")))


(s/def ::volume-option
  (-> (st/spec (su/only-keys-maps {:req-un [::option-key
                                            ::option-value]}))
      (assoc :name "volume-option"
             :json-schema/type "map"
             :json-schema/display-name "volume option"
             :json-schema/description "volume option")))


(s/def ::volume-options
  (-> (st/spec (s/coll-of ::volume-option :kind vector?))
      (assoc :name "volume-options"
             :json-schema/type "array"
             :json-schema/display-name "volume options"
             :json-schema/description "list of volume option"

             :json-schema/indexed false)))



(s/def ::mount
  (-> (st/spec (su/only-keys-maps {:req-un [::mount-type
                                            ::target]
                                   :opt-un [::source
                                            ::read-only
                                            ::volume-options]}))
      (assoc :name "mount"
             :json-schema/type "map"
             :json-schema/description "mount")))


(s/def ::mounts
  (-> (st/spec (s/coll-of ::mount :kind vector?))
      (assoc :name "mounts"
             :json-schema/type "array"
             :json-schema/description "list of mounts"

             :json-schema/indexed false)))
