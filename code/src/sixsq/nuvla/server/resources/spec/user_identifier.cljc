(ns sixsq.nuvla.server.resources.spec.user-identifier
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

; redefine parent to put it as mandatory
(s/def ::parent (-> (st/spec ::user/id)
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/description "reference to parent resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/type "string"
             :json-schema/description "identifier to associate with a user"

             :json-schema/order 10)))


(def ^:const user-identifier-common-attrs
  {:req-un [::common/id
            ::common/resource-type
            ::common/created
            ::common/updated
            ::common/acl
            ::parent]
   :opt-un [::common/name
            ::common/description
            ::common/tags
            ::common/resource-metadata
            ::common/operations
            ::common/created-by
            ::common/updated-by]})


(s/def ::schema
  (su/only-keys-maps user-identifier-common-attrs
                     {:req-un [::parent
                               ::identifier]}))
