(ns sixsq.nuvla.server.resources.spec.data-object
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.data :as data]
    [spec-tools.core :as st]))


(s/def ::subtype
  (-> (st/spec ::common/subtype)
      (assoc :name "subtype"
             :json-schema/type "string"
             :json-schema/description "subtype of data-object"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 30)))


(s/def ::state
  (-> (st/spec #{"NEW" "UPLOADING" "READY"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "data-object state"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 31)))


(def credential-id-regex #"^credential/.*$")


(s/def ::credential
  (-> (st/spec (s/and string? #(re-matches credential-id-regex %)))
      (assoc :name "credential"
             :json-schema/type "string"
             :json-schema/description "credential that provides access to the S3 object"

             :json-schema/editable false
             :json-schema/order 32)))


(s/def ::bucket
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "bucket"
             :json-schema/description "name of the S3 bucket"

             :json-schema/editable false
             :json-schema/order 33)))


(s/def ::object
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "object"
             :json-schema/description "name of the S3 object"

             :json-schema/editable false
             :json-schema/order 34)))


(def data-object-template-regex #"^data-object-template/[a-z]+(-[a-z]+)*$")


(s/def ::href
  (-> (st/spec (s/and string? #(re-matches data-object-template-regex %)))
      (assoc :name "href"
             :json-schema/type "resource-id"
             :json-schema/description "reference to template"

             :json-schema/editable false
             :json-schema/order 35)))


(s/def ::template
  (-> (st/spec (s/and string? #(re-matches data-object-template-regex %)))
      (assoc :name "template"
             :json-schema/type "resource-id"
             :json-schema/description "reference to template"

             :json-schema/editable false
             :json-schema/order 35)))


(def common-data-object-attrs {:req-un [::subtype
                                        ::state
                                        ::object
                                        ::bucket
                                        ::credential]
                               :opt-un [::template
                                        ::data/content-type
                                        ::data/bytes
                                        ::data/md5sum
                                        ::data/timestamp
                                        ::data/location]})


;; :state is server managed and shouldn't appear in a template expansion
(def common-data-object-tpl-attrs {:req-un [::subtype
                                            ::object
                                            ::bucket
                                            ::credential]
                                   :opt-un [::href
                                            ::data/content-type
                                            ::data/bytes
                                            ::data/md5sum
                                            ::data/timestamp
                                            ::data/location]})
