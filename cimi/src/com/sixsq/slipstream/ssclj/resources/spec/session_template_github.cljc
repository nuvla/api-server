(ns com.sixsq.slipstream.ssclj.resources.spec.session-template-github
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template :as ps]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

;; Parameters for the GitHub configuration parameters are picked
;; up from the environment.

;; Defines the contents of the github SessionTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))

;; Defines the contents of the github template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :sessionTemplate here.
(s/def ::sessionTemplate
  (su/only-keys-maps ps/template-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::sessionTemplate]}))
