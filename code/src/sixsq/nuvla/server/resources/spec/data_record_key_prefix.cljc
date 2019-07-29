(ns sixsq.nuvla.server.resources.spec.data-record-key-prefix
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
             :json-schema/description "unique namespace prefix for collections of data record keys"

             :json-schema/editable false
             :json-schema/order 30)))


(s/def ::uri
  (-> (st/spec ::core/uri)
      (assoc :name "URI"
             :json-schema/description "globally-unique URI associated with prefix")))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::prefix
                               ::uri]}))
