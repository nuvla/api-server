(ns com.sixsq.nuvla.server.resources.spec.session-template-oidc
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.session-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]))


;; Defines the contents of the oidc session-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))


;; Defines the contents of the oidc template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
