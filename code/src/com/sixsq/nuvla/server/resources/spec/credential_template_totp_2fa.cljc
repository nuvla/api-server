(ns com.sixsq.nuvla.server.resources.spec.credential-template-totp-2fa
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;;should be encrypted
(s/def ::secret
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "secret"
             :json-schema/description "Two factor authenticaton TOTP secret"

             :json-schema/sensitive true)))


(def credential-template-keys-spec
  {:req-un [::secret]})


(def credential-template-create-keys-spec
  {:req-un [::secret]})


;; Defines the contents of the resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
