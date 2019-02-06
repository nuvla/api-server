(ns sixsq.nuvla.server.resources.spec.credential-template-cloud
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.acl :as cimi-acl]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;; FIXME: For key and secret see https://github.com/slipstream/SlipStreamServer/issues/1309

(s/def ::key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "key"
             :json-schema/name "key"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "key"
             :json-schema/description "key for cloud credential"
             :json-schema/help "key for cloud credential"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::secret
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "secret"
             :json-schema/name "secret"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "secret"
             :json-schema/description "secret for cloud credential"
             :json-schema/help "secret for cloud credential"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::connector
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "connector"
             :json-schema/name "connector"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "connector"
             :json-schema/description "cloud connector"
             :json-schema/help "name of cloud connector for this credential"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::quota
  (-> (st/spec nat-int?)
      (assoc :name "quota"
             :json-schema/name "quota"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "long"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "quota"
             :json-schema/description "virtual machine quota"
             :json-schema/help "maximum number of allocated virtual machines"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::manager
  (-> (st/spec (su/only-keys :req-un [::cimi-acl/principal
                                      ::cimi-acl/type]))
      (assoc :name "manager"
             :json-schema/name "manager"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "manager"
             :json-schema/description "principal and type of manager"
             :json-schema/help "principal and type of manager"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::managers (s/coll-of ::manager :min-count 1 :kind vector?))


(s/def ::managers
       (-> (st/spec (s/coll-of ::manager :min-count 1 :kind vector?))
           (assoc :name "managers"
                  :json-schema/name "managers"
                  :json-schema/namespace common-ns/slipstream-namespace
                  :json-schema/uri common-ns/slipstream-uri
                  :json-schema/type "Array"
                  :json-schema/providerMandatory false
                  :json-schema/consumerMandatory false
                  :json-schema/mutable true
                  :json-schema/consumerWritable true

                  :json-schema/displayName "managers"
                  :json-schema/description "list of credential managers"
                  :json-schema/help "list of credential managers"
                  :json-schema/group "body"
                  :json-schema/order 25
                  :json-schema/hidden false
                  :json-schema/sensitive false)))


(s/def ::disabledMonitoring
       (-> (st/spec boolean?)
           (assoc :name "disabledMonitoring"
                  :json-schema/name "disabledMonitoring"
                  :json-schema/namespace common-ns/slipstream-namespace
                  :json-schema/uri common-ns/slipstream-uri
                  :json-schema/type "boolean"
                  :json-schema/providerMandatory false
                  :json-schema/consumerMandatory false
                  :json-schema/mutable true
                  :json-schema/consumerWritable true

                  :json-schema/displayName "disabledMonitoring"
                  :json-schema/description "periodic monitoring disabled for this credential"
                  :json-schema/help "true to disable periodic monitoring for this credential"
                  :json-schema/group "body"
                  :json-schema/order 26
                  :json-schema/hidden false
                  :json-schema/sensitive false)))


(def credential-template-cloud-keys-spec
  {:req-un [::key
            ::secret
            ::connector
            ::quota]
   :opt-un [::managers
            ::disabledMonitoring]})

(def credential-template-create-keys-spec credential-template-cloud-keys-spec)
