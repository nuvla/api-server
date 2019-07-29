(ns sixsq.nuvla.server.resources.spec.session-template-mitreid-token
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::token
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "token"
             :json-schema/description "OIDC authentication token"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive true)))


;; all parameters must be specified in both the template and the create resource
(def session-template-keys-spec
  {:req-un [::token]})


;; Defines the contents of the oidc-token session-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     session-template-keys-spec))


;; Defines the contents of the oidc-token template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     session-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
