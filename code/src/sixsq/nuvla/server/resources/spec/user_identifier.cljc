(ns sixsq.nuvla.server.resources.spec.user-identifier
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

; redefine parent to put it as mandatory
(s/def ::parent (-> (st/spec ::user/id)
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/display-name "parent"
                           :json-schema/description "reference to parent resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/type "string"
             :json-schema/display-name "identifier"
             :json-schema/description "identifier to associate with a user"

             :json-schema/order 10)))


(def ^:const user-identifier-common-attrs
  {:req-un [::cimi-common/id
            ::cimi-common/resource-type
            ::cimi-common/created
            ::cimi-common/updated
            ::cimi-common/acl
            ::parent]
   :opt-un [::cimi-common/name
            ::cimi-common/description
            ::cimi-common/tags
            ::cimi-common/resource-metadata
            ::cimi-common/operations]})


(s/def ::schema
  (su/only-keys-maps user-identifier-common-attrs
                     {:req-un [::parent
                               ::identifier]}))
