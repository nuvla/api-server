(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infrastructure-service]
    [sixsq.nuvla.server.util.spec :as su]))


;; Restrict the href used to create services.
(def service-template-regex #"^infrastructure-service-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches service-template-regex %)))

;;
;; Keys specifications for service-template resources.
;; As this is a "base class" for service-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def service-template-keys-spec {:req-un [::infrastructure-service/type
                                          ::infrastructure-service/method]})


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
