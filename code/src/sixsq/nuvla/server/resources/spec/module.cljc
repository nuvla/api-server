(ns sixsq.nuvla.server.resources.spec.module
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const path-regex #"^[a-zA-Z0-9][\w\.-]*(/[a-zA-Z0-9][\w\.-]*)*$")


(defn path? [v] (boolean (re-matches path-regex v)))


(defn parent-path? [v] (or (= "" v) (path? v)))


(def ^:const price-id-regex #"^price_.+$")

(defn price-id? [s] (re-matches price-id-regex s))


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
  (-> (st/spec #{"project" "component" "application" "application_kubernetes" "applications_sets"})
      (assoc :name "subtype"
             :json-schema/type "string"
             :json-schema/description "module type"
             :json-schema/value-scope {:values ["project" "component" "application"
                                                "application_kubernetes"]}

             :json-schema/editable false
             :json-schema/fulltext true
             :json-schema/order 34)))


(s/def ::commit (-> (st/spec string?)
                    (assoc :name "commit"
                           :json-schema/type "string"
                           :json-schema/description "commit message")))

(s/def ::author (-> (st/spec string?)
                    (assoc :name "author"
                           :json-schema/type "string"
                           :json-schema/description "author of the commit")))


(s/def ::published
  (-> (st/spec boolean?)
      (assoc :name "published"
             :json-schema/type "boolean"
             :json-schema/description "module is published"
             :json-schema/server-managed true
             :json-schema/editable false)))


(def module-regex #"^module\-(component|application|applications-sets)/[a-z0-9]+(-[a-z0-9]+)*$")

(s/def ::href
  (-> (st/spec (s/and string? #(re-matches module-regex %)))
      (assoc :name "href"
             :json-schema/type "string"
             :json-schema/description "reference to the configuration template used")))


(s/def ::version
  (-> (st/spec (s/nilable
                 (su/only-keys
                   :req-un [::href
                            ::author]
                   :opt-un [::commit
                            ::published])))
      (assoc :name "version"
             :json-schema/type "map"
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
             :json-schema/value-scope {:values ["swarm" "docker-compose"]}


             :json-schema/fulltext true
             :json-schema/order 35)))

(s/def ::valid
  (-> (st/spec boolean?)
      (assoc :name "valid"
             :json-schema/type "boolean"
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
  (-> (st/spec (s/and string? price-id?))
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


(s/def ::currency (-> (st/spec ::core/nonblank-string)
                      (assoc :name "currency"
                             :json-schema/type "string")))


(s/def ::cent-amount-daily
  (-> (st/spec pos-int?)
      (assoc :name "cent-amount-daily"
             :json-schema/type "integer"
             :json-schema/description "cent amount by day")))


(s/def ::follow-customer-trial
  (-> (st/spec boolean?)
      (assoc :name "follow customer trial"
             :json-schema/type "boolean"
             :json-schema/description "follow customer trial?")))

(s/def ::vendor-email
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vendor email"
             :json-schema/type "string"
             :json-schema/server-managed true)))


(s/def ::price
  (-> (st/spec (su/only-keys
                 :req-un [::product-id
                          ::price-id
                          ::account-id
                          ::currency
                          ::cent-amount-daily]
                 :opt-un [::follow-customer-trial
                          ::vendor-email]))
      (assoc :name "price"
             :json-schema/type "map"
             :json-schema/order 38)))


(s/def ::license
  (-> (st/spec (su/only-keys
                 :req-un [::common/name
                          ::core/url]
                 :opt-un [::common/description]))
      (assoc :name "license"
             :json-schema/type "map"
             :json-schema/order 39)))


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
                                                      ::price
                                                      ::license
                                                      ::published]}]))


(s/def ::schema (su/only-keys-maps module-keys-spec))
