(ns com.sixsq.nuvla.server.resources.spec.email
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::address
  (-> (st/spec ::core/email)
      (assoc :name "address"
             :json-schema/description "email address"
             :json-schema/editable false
             :json-schema/order 10)))


(s/def ::validated
  (-> (st/spec boolean?)
      (assoc :name "validated"
             :json-schema/type "boolean"
             :json-schema/description "validated email address?"
             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 11)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::address]
                      :opt-un [::validated]}))
