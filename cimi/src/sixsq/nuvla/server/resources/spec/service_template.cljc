(ns sixsq.nuvla.server.resources.spec.service-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.service :as service]
    [sixsq.nuvla.server.util.spec :as su]))


;; Restrict the href used to create services.
(def service-template-regex #"^service-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches service-template-regex %)))

;;
;; Keys specifications for service-template resources.
;; As this is a "base class" for service-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def service-template-keys-spec {:req-un [::service/type
                                          ::service/method]})


(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        service-template-keys-spec]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))


(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))


(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        service-template-keys-spec
                        {:req-un [::href]}]))
