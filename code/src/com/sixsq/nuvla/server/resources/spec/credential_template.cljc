(ns com.sixsq.nuvla.server.resources.spec.credential-template
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def ^:const credential-id-regex #"^credential/[0-9a-f]+(-[0-9a-f]+)*$")

(s/def ::credential-id (s/and string? #(re-matches credential-id-regex %)))

(def credential-id-spec
  (assoc (st/spec ::credential-id)
    :name "credential-id"
    :json-schema/type "resource-id"
    :json-schema/description "reference to credential resource"))

;; All credential templates must indicate the subtype of credential to create.
(s/def ::subtype
  (assoc (st/spec ::common/subtype)
    :name "subtype"
    :json-schema/description "subtype of credential"

    :json-schema/order 0
    :json-schema/hidden true))

;; A given credential may have more than one method for creating it.  All
;; credential templates must provide a method name.
(s/def ::method
  (assoc (st/spec ::core/identifier)
    :name "method"
    :json-schema/description "method for creating credential"

    :json-schema/order 1
    :json-schema/hidden true))

(s/def ::last-check
  (assoc (st/spec ::core/timestamp)
    :name "last-check"
    :json-schema/type "date-time"
    :json-schema/description "latest resource check timestamp (UTC)"
    :json-schema/section "meta"

    :json-schema/server-managed true
    :json-schema/editable false))

(s/def ::status
  (assoc (st/spec #{"VALID", "INVALID", "UNKNOWN"})
    :name "status"
    :json-schema/type "string"
    :json-schema/description "status of credential at last-check date"

    :json-schema/value-scope {:values ["VALID", "INVALID", "UNKNOWN"]}))

(def credential-keys-spec (su/merge-keys-specs
                            [common/common-attrs
                             {:req-un [::subtype
                                       ::method]
                              :opt-un [::last-check
                                       ::status]}]))

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
                        credential-keys-spec]))

;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))

(def create-keys-spec
  (su/merge-keys-specs [common/create-attrs]))

;; subclasses MUST provide the href to the template to use
(def template-keys-spec
  (su/merge-keys-specs [common/template-attrs
                        credential-template-keys-spec-opt]))

