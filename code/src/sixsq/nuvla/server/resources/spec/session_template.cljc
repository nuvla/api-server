(ns sixsq.nuvla.server.resources.spec.session-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; All session resources must have a 'method' attribute.
(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/description "authentication method"
             :json-schema/order 0
             :json-schema/hidden true)))


;; All session resources must have a 'instance' attribute that is used in
;; the template identifier.
(s/def ::instance
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "instance"
             :json-schema/description "instance name of authentication method"
             :json-schema/order 1
             :json-schema/hidden true)))


;; Restrict the href used to create sessions.
(def session-template-regex #"^session-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches session-template-regex %)))

;;
;; Keys specifications for SessionTemplate resources.
;; As this is a "base class" for SessionTemplate resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def session-template-keys-spec {:req-un [::method ::instance]})

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        hints/ui-hints-spec
                        session-template-keys-spec]))

;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))

(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        hints/ui-hints-spec
                        session-template-keys-spec
                        {:req-un [::href]}]))

