(ns sixsq.nuvla.server.resources.spec.data-record-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")


(s/def ::prefix
  (-> (st/spec (s/and string? #(re-matches prefix-regex %)))
      (assoc :name "prefix"
             :json-schema/type "string"
             :json-schema/description "namespace prefix for data record key"

             :json-schema/editable false
             :json-schema/order 30)))


(s/def ::key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "key"
             :json-schema/description "unique name of key within prefix namespace"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 30)))


(s/def ::subtype ::common/subtype)


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::common/name                ;; name is required
                               ::common/description         ;; description is required
                               ::prefix
                               ::key
                               ::subtype]}))
