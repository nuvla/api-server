(ns sixsq.nuvla.server.resources.spec.credential-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::subtype ::cred/subtype)


(s/def ::method ::cred/method)


(def credential-template-regex #"^credential-template/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$")

(s/def :cimi.credential-template/href (s/and string? #(re-matches credential-template-regex %)))


;;
;; Keys specifications for CredentialTemplate resources.
;; As this is a "base class" for CredentialTemplate resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def credential-template-keys-spec {:req-un [::subtype ::method]})

(def credential-template-keys-spec-opt {:opt-un [::subtype ::method]})

(def resource-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        cred/credential-keys-spec]))

;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))

(def create-keys-spec
  (su/merge-keys-specs [common/create-attrs]))

;; subclasses MUST provide the href to the template to use
(def template-keys-spec
  (su/merge-keys-specs [common/template-attrs
                        credential-template-keys-spec-opt]))

