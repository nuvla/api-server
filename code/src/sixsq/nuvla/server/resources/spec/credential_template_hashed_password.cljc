(ns sixsq.nuvla.server.resources.spec.credential-template-hashed-password
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::password
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/display-name "password"
             :json-schema/description "plaintext password"

             :json-schema/order 21
             :json-schema/sensitive true)))


(def credential-template-keys-spec
  {:opt-un [::password]})


(def credential-template-create-keys-spec
  {:req-un [::password]})


;; Defines the contents of the resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     credential-template-create-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
