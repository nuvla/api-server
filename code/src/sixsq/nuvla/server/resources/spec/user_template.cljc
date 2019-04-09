(ns sixsq.nuvla.server.resources.spec.user-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; All user templates must indicate the method used to create the user.
(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/description "user creation method"

             :json-schema/order 0
             :json-schema/hidden true)))


;; All user template resources must have a 'instance' attribute that is used as
;; the template identifier.
(s/def ::instance
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "instance"
             :json-schema/description "instance name of user creation method"

             :json-schema/order 1
             :json-schema/hidden true)))


(def user-template-regex #"^user-template/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$")

(s/def ::href
  (-> (st/spec (s/and string? #(re-matches user-template-regex %)))
      (assoc :name "href"
             :json-schema/type "map"
             :json-schema/display-name "user template"
             :json-schema/description "reference to the user template"

             :json-schema/order 0
             :json-schema/hidden true)))

;;
;; Keys specifications for user-template resources.
;; As this is a "base class" for user-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def user-template-keys-spec {:req-un [::method ::instance]})

(def user-template-template-keys-spec {:req-un [::instance]
                                       :opt-un [::method]})

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        hints/ui-hints-spec
                        user-template-keys-spec]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))


(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

;; subclasses MUST provide the href to the template to use
(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        hints/ui-hints-spec
                        user-template-template-keys-spec]))

