(ns com.sixsq.nuvla.server.resources.spec.infrastructure-service-template
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; Restrict the href used to create services.
(def service-template-regex #"^infrastructure-service-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches service-template-regex %)))


(s/def ::method
  (-> (st/spec ::core/identifier)
      (assoc :name "method"
             :json-schema/description "service creation method"

             :json-schema/order 20
             :json-schema/hidden true)))


(s/def ::subtype
  (-> (st/spec ::core/identifier)
      (assoc :name "subtype"
             :json-schema/display-name "service subtype"
             :json-schema/description "kebab-case identifier for the service subtype"

             :json-schema/order 21)))
;;
;; Keys specifications for service-template resources.
;; As this is a "base class" for service-template resources, there
;; is no sense in defining map resources for the resource itself.
;;


(def service-template-keys-spec {:req-un [::subtype
                                          ::method]})


(def resource-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        service-template-keys-spec]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))


(def create-keys-spec
  (su/merge-keys-specs [common/create-attrs]))


(def template-keys-spec
  (su/merge-keys-specs [common/template-attrs
                        service-template-keys-spec
                        {:req-un [::href]}]))
