(ns com.sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-helm-repo
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "username"
             :json-schema/description "username"

             :json-schema/order 21
             :json-schema/sensitive true)))

(s/def ::password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "password"
             :json-schema/description "plaintext password"

             :json-schema/order 21
             :json-schema/sensitive true)))


(def credential-template-keys-spec-opt
  {:opt-un [::username
            ::password]})


(def credential-template-keys-spec-req
  {:req-un [::username
            ::password]})


;; Defines the contents of the credential-template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     credential-template-keys-spec-opt))


;; Defines the contents of the credential-template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ct/template-keys-spec
                                  credential-template-keys-spec-req))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
