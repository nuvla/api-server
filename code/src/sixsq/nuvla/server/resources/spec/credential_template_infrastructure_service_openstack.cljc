(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-openstack
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::openstack-username
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "openstack-username"
             :json-schema/type "string"
             :json-schema/description "Openstack username to login with"
             :json-schema/order 20)))


(s/def ::openstack-password
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "openstack-password"
             :json-schema/type "string"
             :json-schema/description "Password for the Openstack username"
             :json-schema/order 21
             :json-schema/sensitive true)))


(s/def ::openstack-tenant-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
    (assoc :name "openstack-tenant-id"
           :json-schema/type "string"
           :json-schema/description "Openstack tenant ID"
           :json-schema/order 22)))


(s/def ::openstack-domain-name
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
    (assoc :name "openstack-domain-name"
           :json-schema/type "string"
           :json-schema/description "Openstack domain name"
           :json-schema/order 23)))


(s/def ::openstack-auth-url
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
    (assoc :name "openstack-auth-url"
           :json-schema/type "string"
           :json-schema/description "Openstack authorization endpoint"
           :json-schema/order 24)))


(def credential-template-keys-spec
  {:req-un [::openstack-username
            ::openstack-password
            ::openstack-tenant-id
            ::openstack-domain-name
            ::openstack-auth-url]})

(def credential-template-create-keys-spec
  {:req-un [::openstack-username
            ::openstack-password
            ::openstack-tenant-id
            ::openstack-domain-name
            ::openstack-auth-url]})

;; Defines the contents of the openstack CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the openstack template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ct/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
