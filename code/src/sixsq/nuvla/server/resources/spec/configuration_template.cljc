(ns sixsq.nuvla.server.resources.spec.configuration-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::service
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "service"
             :json-schema/name "service"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "service"
             :json-schema/description "name of service associated with this resource"
             :json-schema/order 10
             :json-schema/hidden true)))


(s/def ::instance
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "instance"
             :json-schema/name "instance"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "instance"
             :json-schema/description "instance of service associated with this resource"
             :json-schema/order 11)))


(def configuration-template-regex #"^configuration-template/[a-z0-9]+(-[a-z0-9]+)*$")

(s/def ::href
  (-> (st/spec (s/and string? #(re-matches configuration-template-regex %)))
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "href"
             :json-schema/description "reference to the configuration template used"
             :json-schema/order 12)))


(s/def ::template
  (-> (st/spec (su/only-keys-maps {:req-un [::href]}))
      (assoc :name "template"
             :json-schema/name "template"
             :json-schema/type "map"

             :json-schema/display-name "template"
             :json-schema/description "reference to the configuration template used"
             :json-schema/order 13)))


;;
;; Keys specifications for configuration-template resources.
;; As this is a "base class" for configuration-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def configuration-template-keys-spec {:req-un [::service]
                                       :opt-un [::instance ::template]})

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs configuration-template-keys-spec]))

(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        configuration-template-keys-spec
                        {:opt-un [::href]}]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))
