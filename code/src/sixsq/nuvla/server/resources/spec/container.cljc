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
             :json-schema/type "URI"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "registry"
             :json-schema/description "registry"
             :json-schema/help "registry"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::repository
  (-> (st/spec ::cimi-core/token)
      (assoc :name "repository"
             :json-schema/name "repository"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "repository"
             :json-schema/description "repository"
             :json-schema/help "repository"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::image-name
  (-> (st/spec ::cimi-core/token)
      (assoc :name "image-name"
             :json-schema/name "image-name"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "image name"
             :json-schema/description "image name"
             :json-schema/help "image name"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::tag
  (-> (st/spec ::cimi-core/token)
      (assoc :name "tag"
             :json-schema/name "tag"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "tag"
             :json-schema/description "tag"
             :json-schema/help "tag"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false
             :json-schema/value-scope {:default "latest"})))


(s/def ::image
  (-> (st/spec (su/only-keys-maps {:req-un [::image-name]
                                   :opt-un [::registry
                                            ::tag
                                            ::repository]}))
      (assoc :name "image"
             :json-schema/name "image"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "image"
             :json-schema/description "image"
             :json-schema/help "image"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


;; ports spec

(s/def ::protocol
  (-> (st/spec #{"tcp" "udp" "sctp"})
      (assoc :name "image"
             :json-schema/name "protocol"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "protocol"
             :json-schema/description "protocol"
             :json-schema/help "protocol"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false
             :json-schema/value-scope {:values  ["tcp", "udp", "sctp"]
                                       :default "tcp"})))


(s/def ::target-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "target-port"
             :json-schema/name "target-port"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "target port"
             :json-schema/description "target port"
             :json-schema/help "target port"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::published-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "published-port"
             :json-schema/name "published-port"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "published port"
             :json-schema/description "published port"
             :json-schema/help "published port"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::port
  (-> (st/spec (su/only-keys-maps {:req-un [::target-port]
                                   :opt-un [::protocol
                                            ::published-port]}))
      (assoc :name "port"
             :json-schema/name "port"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "port"
             :json-schema/description "port"
             :json-schema/help "port"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::ports
  (-> (st/spec (s/coll-of ::port :kind vector?))
      (assoc :name "ports"
             :json-schema/name "ports"
             :json-schema/type "Array"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/indexed false

             :json-schema/display-name "ports"
             :json-schema/description "list of port"
             :json-schema/help "list of port"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


;; mounts spec


(s/def ::source
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "source"
             :json-schema/name "source"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "source"
             :json-schema/description "source"
             :json-schema/help "source"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::target
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "target"
             :json-schema/name "target"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "target"
             :json-schema/description "target"
             :json-schema/help "target"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::mount-type
  (-> (st/spec #{"bind", "volume"})
      (assoc :name "image"
             :json-schema/name "mount-type"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "mount type"
             :json-schema/description "mount type"
             :json-schema/help "mount type"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false
             :json-schema/value-scope {:values ["bind", "volume"]})))


(s/def ::read-only
  (-> (st/spec boolean?)
      (assoc :name "read-only"
             :json-schema/name "read-only"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "read only"
             :json-schema/description "read only"
             :json-schema/help "read only"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::option-key
  (-> (st/spec ::cimi-core/token)
      (assoc :name "option-key"
             :json-schema/name "option-key"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "option key"
             :json-schema/description "option key"
             :json-schema/help "option key"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::option-value
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "option-value"
             :json-schema/name "option-value"
             :json-schema/type "string"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "option value"
             :json-schema/description "option value"
             :json-schema/help "option value"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::volume-option
  (-> (st/spec (su/only-keys-maps {:req-un [::option-key
                                            ::option-value]}))
      (assoc :name "volume-option"
             :json-schema/name "volume-option"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "volume option"
             :json-schema/description "volume option"
             :json-schema/help "volume option"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::volume-options
  (-> (st/spec (s/coll-of ::volume-option :kind vector?))
      (assoc :name "volume-options"
             :json-schema/name "volume-options"
             :json-schema/type "Array"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/indexed false

             :json-schema/display-name "volume options"
             :json-schema/description "list of volume option"
             :json-schema/help "list of volume option"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))



(s/def ::mount
  (-> (st/spec (su/only-keys-maps {:req-un [::mount-type
                                            ::source
                                            ::target]
                                   :opt-un [::read-only
                                            ::volume-options]}))
      (assoc :name "mount"
             :json-schema/name "mount"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/display-name "mount"
             :json-schema/description "mount"
             :json-schema/help "mount"
             :json-schema/group "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::mounts
  (-> (st/spec (s/coll-of ::mount :kind vector?))
      (assoc :name "mounts"
             :json-schema/name "mounts"
             :json-schema/type "Array"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true
             :json-schema/indexed false

             :json-schema/display-name "mounts"
             :json-schema/description "list of mount"
             :json-schema/help "list of mount"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/hidden false
             :json-schema/sensitive false)))