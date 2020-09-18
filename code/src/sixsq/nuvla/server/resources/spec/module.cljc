(ns sixsq.nuvla.server.resources.spec.module
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [sixsq.nuvla.server.resources.spec.pricing :as pricing]
    [spec-tools.core :as st]))


(def ^:const path-regex #"^[a-zA-Z0-9][\w\.-]*(/[a-zA-Z0-9][\w\.-]*)*$")


(defn path? [v] (boolean (re-matches path-regex v)))


(defn parent-path? [v] (or (= "" v) (path? v)))


(s/def ::path
  (-> (st/spec (s/and string? path?))
      (assoc :name "path"
             :json-schema/type "string"
             :json-schema/description "module path"
             :json-schema/fulltext true
             :json-schema/order 32)))


(s/def ::parent-path
  (-> (st/spec (s/and string? parent-path?))
      (assoc :name "parent-path"
             :json-schema/type "string"
             :json-schema/display-name "parent path"
             :json-schema/description "parent path for module"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 33)))


(s/def ::subtype
  (-> (st/spec #{"project" "component" "application" "application_kubernetes"})
      (assoc :name "subtype"
             :json-schema/type "string"
             :json-schema/description "module type"

             :json-schema/editable false
             :json-schema/fulltext true
             :json-schema/order 34)))


(s/def ::version
  (-> (st/spec (s/nilable ::core/resource-link))
      (assoc :name "version"
             :json-schema/type "resource-id"
             :json-schema/description "module version identifier"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/indexed false)))


(s/def ::versions
  (-> (st/spec (s/coll-of ::version :min-count 1))
      (assoc :name "versions"
             :json-schema/type "array"
             :json-schema/description "list of module versions"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/indexed false
             :json-schema/order 35)))


(s/def ::logo-url
  (-> (st/spec ::core/url)
      (assoc :name "logo-url"
             :json-schema/display-name "logo URL"
             :json-schema/description "URL for the module's logo"

             :json-schema/order 36)))


(s/def ::content
  (-> (st/spec map?)
      (assoc :name "content"
             :json-schema/type "map"
             :json-schema/description "module content"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/indexed false
             :json-schema/order 37)))


;;
;; data management attributes
;;

(s/def ::data-accept-content-types
  (-> (st/spec (s/coll-of ::core/mimetype :kind vector?))
      (assoc :name "data-accept-content-types"
             :json-schema/type "array"
             :json-schema/display-name "accepted content types"
             :json-schema/description "list of accepted data content types"

             :json-schema/fulltext true
             :json-schema/order 37)))


(s/def ::data-access-protocols
  (-> (st/spec (s/coll-of ::core/token :kind vector?))
      (assoc :name "data-access-protocols"
             :json-schema/type "array"
             :json-schema/display-name "data access protocols"
             :json-schema/description "list of data access protocols understood by module"

             :json-schema/fulltext true
             :json-schema/order 38)))


;;
;; compute attributes: only valid for Docker modules atm
;;

(s/def ::compatibility
  (-> (st/spec #{"swarm" "docker-compose"})
    (assoc :name "compatibility"
           :json-schema/type "string"
           :json-schema/description "module compatibility"
           :json-schema/server-managed true
           :json-schema/editable false

           :json-schema/fulltext true
           :json-schema/order 35)))

(s/def ::valid
  (-> (st/spec boolean?)
      (assoc :name "valid"
             :json-schema "boolean"
             :json-schema/description "content is valid"
             :json-schema/order 36)))


(s/def ::validation-message
  (-> (st/spec string?)
      (assoc :name "message"
             :json-schema/type "string"
             :json-schema/order 37)))

(def ^:const product-id-regex #"^prod_.+$")

(defn product-id? [s] (re-matches product-id-regex s))

(s/def ::product-id
  (-> (st/spec (s/and string? product-id?))
      (assoc :name "product-id"
             :json-schema/type "string"
             :json-schema/description "identifier of product id"
             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::price-id
  (-> (st/spec (s/and string? pricing/price-id?))
      (assoc :name "price-id"
             :json-schema/type "string"
             :json-schema/description "identifier of price id"
             :json-schema/server-managed true
             :json-schema/editable false)))


(def ^:const account-id-regex #"^acct_.+$")

(defn account-id? [s] (re-matches account-id-regex s))

(s/def ::account-id
  (-> (st/spec (s/and string? account-id?))
      (assoc :name "account-id"
             :json-schema/type "string"
             :json-schema/description "identifier of account id"
             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::cent-amount-hourly
  (-> (st/spec pos-int?)
      (assoc :name "cent-amount-hourly"
             :json-schema/type "integer"
             :json-schema/description "cent amount by hour")))


(s/def ::price
  (-> (st/spec (su/only-keys
                 :req-un [::product-id
                          ::price-id
                          ::account-id
                          ::pricing/currency
                          ::cent-amount-hourly]))
      (assoc :name "price"
             :json-schema/type "map"
             :json-schema/order 38)))


(def module-keys-spec (su/merge-keys-specs [common/common-attrs
                                            {:req-un [::path
                                                      ::parent-path
                                                      ::subtype]
                                             :opt-un [::logo-url
                                                      ::data-accept-content-types
                                                      ::data-access-protocols
                                                      ::versions
                                                      ::content
                                                      ::compatibility
                                                      ::valid
                                                      ::validation-message
                                                      ::price]}]))


(s/def ::schema (su/only-keys-maps module-keys-spec))
