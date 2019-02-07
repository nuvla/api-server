(ns sixsq.nuvla.server.resources.spec.connector-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;; Generic type definitions.

(s/def ::identifier (s/and string? #(re-matches #"^[a-z0-9]+(-[a-z0-9]+)*$" %)))

;; Required parameters for all connectors.

(s/def ::cloudServiceType
  (-> (st/spec ::identifier)
      (assoc :name "cloudServiceType"
             :json-schema/name "cloudServiceType"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "cloud.service.type"
             :json-schema/description "type of cloud service targeted by connector"
             :json-schema/help "type of cloud service targeted by connector"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::instanceName
  (-> (st/spec ::identifier)
      (assoc :name "instanceName"
             :json-schema/name "instanceName"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "instance.name"
             :json-schema/description "string that identifies an instance of a particular cloud service type"
             :json-schema/help "string that identifies an instance of a particular cloud service type"
             :json-schema/group "body"
             :json-schema/order 31
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::orchestratorImageid
  (-> (st/spec string?)
      (assoc :name "orchestratorImageid"
             :json-schema/name "orchestratorImageid"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "orchestrator.image.id"
             :json-schema/description "identifier of image to be used to orchestrator"
             :json-schema/help "identifier of image to be used to orchestrator"
             :json-schema/group "body"
             :json-schema/order 32
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::quotaVm
  (-> (st/spec string?)                                     ;; FIXME: Should be nat-int? with 0 indicating no quota.
      (assoc :name "quotaVm"
             :json-schema/name "quotaVm"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "quota.vm"
             :json-schema/description "VM quota for the connector (i.e. maximum number of VMs allowed)"
             :json-schema/help "VM quota for the connector (i.e. maximum number of VMs allowed)"
             :json-schema/group "body"
             :json-schema/order 33
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::maxIaasWorkers
  (-> (st/spec pos-int?)
      (assoc :name "maxIaasWorkers"
             :json-schema/name "maxIaasWorkers"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "max.iaas.workers"
             :json-schema/description "maximum number of concurrently provisioned VMs by orchestrator"
             :json-schema/help "maximum number of concurrently provisioned VMs by orchestrator"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


;; Common parameters, but which are not used by all.
;; Add these to the connector schema as necessary.

(s/def ::endpoint
  (-> (st/spec string?)
      (assoc :name "endpoint"
             :json-schema/name "endpoint"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "endpoint"
             :json-schema/description "service endpoint for the connector (e.g. http://example.com:5000)"
             :json-schema/help "service endpoint for the connector (e.g. http://example.com:5000)"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::objectStoreEndpoint
  (-> (st/spec string?)
      (assoc :name "objectStoreEndpoint"
             :json-schema/name "objectStoreEndpoint"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "object store endpoint"
             :json-schema/description "cloud object store service endpoint (e.g. http://s3.example.com:5000)"
             :json-schema/help "cloud object store service endpoint (e.g. http://s3.example.com:5000)"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::nativeContextualization
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "nativeContextualization"
             :json-schema/name "nativeContextualization"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "native contextualization"
             :json-schema/description "use native cloud contextualization"
             :json-schema/help "Here you can define when SlipStream should use the native Cloud contextualization or when it should try other methods like SSH and WinRM."
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:values  ["never" "linux-only" "windows-only" "always"]
                                       :default "linux-only"})))


(s/def ::orchestratorSSHUsername
  (-> (st/spec string?)
      (assoc :name "orchestratorSSHUsername"
             :json-schema/name "orchestratorSSHUsername"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "orchestrator.ssh.username"
             :json-schema/description "orchestrator SSH username"
             :json-schema/help "Username used to contextualize the orchestrator VM. Leave this field empty if you are using a native Cloud contextualization."
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::orchestratorSSHPassword
  (-> (st/spec string?)
      (assoc :name "orchestratorSSHPassword"
             :json-schema/name "orchestratorSSHPassword"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "orchestrator.ssh.password"
             :json-schema/description "orchestrator SSH password"
             :json-schema/help "Password used to contextualize the orchestrator VM. Leave this field empty if you are using a native cloud contextualization."
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::securityGroups
  (-> (st/spec string?)
      (assoc :name "securityGroups"
             :json-schema/name "securityGroups"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "security.groups"
             :json-schema/description "Orchestrator security groups (comma separated list)"
             :json-schema/help "Orchestrator security groups (comma separated list)"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::updateClientURL
  (-> (st/spec string?)
      (assoc :name "updateClientURL"
             :json-schema/name "updateClientURL"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "update.clienturl"
             :json-schema/description "URL pointing to the tarball containing the client for the connector"
             :json-schema/help "URL pointing to the tarball containing the client for the connector"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))



(def connector-template-regex #"^connector-template/[a-z0-9]+(-[a-z0-9]+)*$")
(s/def ::href (s/and string? #(re-matches connector-template-regex %)))


(s/def ::template (su/only-keys :req-un [::href]))

;;
;; Keys specifications for ConnectorTemplate resources.
;; As this is a "base class" for ConnectorTemplate resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def connector-template-keys-spec {:req-un [::cloudServiceType
                                            ::instanceName
                                            ::orchestratorImageid
                                            ::quotaVm
                                            ::maxIaasWorkers]
                                   :opt-un [::template]})

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs connector-template-keys-spec]))

(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        connector-template-keys-spec
                        {:opt-un [::href]}]))

