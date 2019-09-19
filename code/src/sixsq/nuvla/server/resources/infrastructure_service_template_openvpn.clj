(ns sixsq.nuvla.server.resources.infrastructure-service-template-openvpn
  "
Template that requires the information necessary to create and manage a new
Docker Swarm cluster on a given cloud infrastructure.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-openvpn :as tpl-openvpn]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const subtype "openvpn")


(def ^:const method "openvpn")


(def template {:method                 method

               :name                   "create openvpn service template"
               :description            "template to create docker openvpn"
               :resource-metadata      (str "resource-metadata/"
                                            infra-service-tpl/resource-type "-" method)

               :subtype                subtype

               :acl                    infra-service-tpl/resource-acl

               :instance               "nuvlabox-vpn"

               :openvpn-ca-certificate "ca certif"
               :openvpn-scope          "nuvlabox"
               :openvpn-endpoints      [{:protocol "udp"
                                         :port     1194
                                         :endpoint "openvpn.endpoint.example"}]})


;;
;; initialization: register this template and provide metadata description
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::tpl-openvpn/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::infra-service-tpl/ns
                                                        ::tpl-openvpn/schema-create "create"))


(defn initialize
  []
  (infra-service-tpl/register template)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-openvpn/schema))


(defmethod infra-service-tpl/validate-subtype method
  [resource]
  (validate-fn resource))
