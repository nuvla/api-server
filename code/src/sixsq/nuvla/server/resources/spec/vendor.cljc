(ns sixsq.nuvla.server.resources.spec.vendor
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::account-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "account-id"
             :json-schema/type "string"
             :json-schema/description "stripe express account id"

             :json-schema/order 20)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::account-id]}))
