(ns sixsq.nuvla.server.resources.spec.credential
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const credential-id-regex #"^credential/[0-9a-f]+(-[0-9a-f]+)*$")


(def credential-id-spec (-> (st/spec (s/and string? #(re-matches credential-id-regex %)))
                            (assoc :name "credential-id"
                                   :json-schema/type "resource-id"
                                   :json-schema/description "reference to credential resource")))


;; All credential templates must indicate the subtype of credential to create.
(s/def ::subtype
  (-> (st/spec ::common/subtype)
      (assoc :name "subtype"
             :json-schema/description "subtype of credential"

             :json-schema/order 0
             :json-schema/hidden true)))


;; A given credential may have more than one method for creating it.  All
;; credential templates must provide a method name.
(s/def ::method
  (-> (st/spec ::core/identifier)
      (assoc :name "method"
             :json-schema/description "method for creating credential"

             :json-schema/order 1
             :json-schema/hidden true)))



(s/def ::last-check (-> (st/spec ::core/timestamp)
                        (assoc :name "last-check"
                               :json-schema/type "date-time"
                               :json-schema/description "latest resource check timestamp (UTC)"
                               :json-schema/section "meta"

                               :json-schema/server-managed true
                               :json-schema/editable false)))


(s/def ::status (-> (st/spec #{"VALID", "INVALID"})
                    (assoc :name "status"
                           :json-schema/type "string"
                           :json-schema/description "status of credential at last-check date"

                           :json-schema/value-scope {:values ["VALID", "INVALID"]})))


(def credential-keys-spec (su/merge-keys-specs [common/common-attrs
                                                {:req-un [::subtype
                                                          ::method]
                                                 :opt-un [::last-check
                                                          ::status]}]))
