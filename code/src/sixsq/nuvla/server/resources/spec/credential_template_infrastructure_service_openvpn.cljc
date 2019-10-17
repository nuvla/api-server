(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-openvpn
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::openvpn-csr
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "openvpn-csr"
             :json-schema/type "integer"
             :json-schema/display-name "openvpn csr"
             :json-schema/description "openvpn certifcate signing request"

             :json-schema/order 20)))


(def credential-template-keys-spec
  {:req-un [::common/parent]
   :opt-un [::openvpn-csr]})


(def credential-template-create-keys-spec
  {:req-un [::common/parent]
   :opt-un [::openvpn-csr]})


;; Defines the contents of the api-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
