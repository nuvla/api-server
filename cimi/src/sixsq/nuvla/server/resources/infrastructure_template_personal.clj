(ns sixsq.nuvla.server.resources.infrastructure-template-personal
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-template-personal :as tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const infra-type "personal")


(def ^:const resource-name "personal infrastructure")


(def ^:const resource-url infra-type)


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

(def default-template {:type        infra-type
                       :name        resource-name
                       :description "import an existing infrastructure to Nuvla"
                       :endpoint    "https://1.1.1.1:2376"
                       :tls-ca      "-----BEGIN CERTIFICATE-----"
                       :tls-cert    "-----BEGIN CERTIFICATE-----"
                       :tls-key     "-----BEGIN RSA PRIVATE KEY-----"
                       :acl         resource-acl})


;;
;; initialization: register this template
;;
(defn initialize
  []
  (p/register infra-type)
  (std-crud/initialize p/resource-type ::tpl/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::tpl/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl/schema))
(defmethod p/validate-subtype infra-type
  [resource]
  (validate-fn resource))
