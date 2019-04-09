(ns sixsq.nuvla.server.resources.spec.email
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::address
  (-> (st/spec ::cimi-core/email)
      (assoc :name "address"
             :json-schema/type "string"

             :json-schema/display-name "address"
             :json-schema/description "email address"
             :json-schema/order 10)))


(s/def ::validated
  (-> (st/spec boolean?)
      (assoc :name "validated"
             :json-schema/type "boolean"

             :json-schema/display-name "validated"
             :json-schema/description "validated email address?"
             :json-schema/order 11)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:req-un [::address]
                      :opt-un [::validated]}))
