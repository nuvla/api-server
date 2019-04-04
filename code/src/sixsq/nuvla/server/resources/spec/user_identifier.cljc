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
                           :json-schema/name "parent"
                           :json-schema/type "uri"
                           :json-schema/required true
                           :json-schema/editable false
                           :json-schema/display-name "parent"
                           :json-schema/description "reference to parent resource"
                           :json-schema/help "reference to the unique resource identifier of the parent resource"
                           :json-schema/group "metadata"
                           :json-schema/category "CIMI common attributes"
                           :json-schema/order 6
                           :json-schema/hidden false
                           :json-schema/sensitive false)))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/name "identifier"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "identifier"
             :json-schema/description "identifier to associate with a user"
             :json-schema/help "unique (external) identifier to associate with a user"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden false
             :json-schema/sensitive false)))

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
