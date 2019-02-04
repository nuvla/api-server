(ns com.sixsq.slipstream.ssclj.resources.spec.session-template-oidc
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template :as ps]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


;; Defines the contents of the oidc SessionTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))

;; Defines the contents of the oidc template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :sessionTemplate here.
(s/def ::sessionTemplate
  (su/only-keys-maps ps/template-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::sessionTemplate]}))
