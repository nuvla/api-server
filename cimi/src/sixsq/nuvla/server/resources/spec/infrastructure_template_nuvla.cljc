(ns sixsq.nuvla.server.resources.spec.infrastructure-template-nuvla
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.infrastructure-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::driver
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "driver"
             :json-schema/name "driver"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "driver"
             :json-schema/description "name of the driver (cloud) in docker-machine"
             :json-schema/help "the infrastructure will be deployed in this cloud provider"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::worker-nodes
  (-> (st/spec nat-int?)
      (assoc :name "worker-nodes"
             :json-schema/name "worker-nodes"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "integer"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "worker-nodes"
             :json-schema/description "number of nodes in the infrastructure, apart from the master"
             :json-schema/help "number of worker nodes to join the single master infrastructure. Can be zero"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::credential
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "credential"
             :json-schema/name "credential"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "credential"
             :json-schema/description "cloud credential"
             :json-schema/help "credential resource to be used for the deployment"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def infrastructure-template-keys-spec
  {:req-un [::driver
            ::credential]
   :opt-un [::worker-nodes]})


;; Defines the contents of the internal SessionTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     infrastructure-template-keys-spec))


;; Defines the contents of the internal template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     infrastructure-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
