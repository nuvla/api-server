(ns com.sixsq.nuvla.server.resources.spec.session-template-github
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.session-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]))

;; Parameters for the GitHub configuration parameters are picked
;; up from the environment.

;; Defines the contents of the github session-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))


;; Defines the contents of the github template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
