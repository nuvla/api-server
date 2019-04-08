(ns sixsq.nuvla.server.resources.spec.core
  "Spec definitions for basic types used in CIMI resources."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.common.utils :as cu]
    [spec-tools.core :as st]))


(s/def ::scalar
  (-> (st/spec (s/or :string string?
                     :double double?
                     :integer int?
                     :boolean boolean?))
      (assoc :name "scalar"
             :json-schema/description "valid scalar value for JSON")))


(def uuid-regex #"^[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$")


(s/def ::uuid
  (-> (st/spec (s/and string? #(re-matches uuid-regex %)))
      (assoc :name "uuid"
             :json-schema/description "UUID"
             :json-schema/type "string")))


(s/def ::nonblank-string
  (-> (st/spec (s/and string? (complement str/blank?)))
      (assoc :name "non-blank string"
             :json-schema/description "string containing something other than only whitespace"
             :json-schema/type "string")))


(defn token? [s] (re-matches #"^\S+$" s))
(s/def ::token
  (-> (st/spec (s/and string? token?))
      (assoc :name "token"
             :json-schema/description "a sequence of one or more non-whitespace characters"
             :json-schema/type "string")))


(s/def ::port
  (-> (st/spec (s/int-in 1 65536))
      (assoc :name "port"
             :json-schema/description "port number in the range 1 to 65535"
             :json-schema/type "integer"

             :json-schema/value-scope {:minimum 1
                                       :maximum 65535})))


;; FIXME: Provide an implementation that works with ClojureScript.
(s/def ::timestamp
  (-> (st/spec cu/as-datetime)
      (assoc :name "timestamp"
             :json-schema/description "UTC timestamp"
             :json-schema/type "date-time")))


;; FIXME: Replace this spec with one that enforces the URI grammar.
(s/def ::uri
  (-> (st/spec ::nonblank-string)
      (assoc :name "URI"
             :json-schema/description "Uniform Resource Identifier"
             :json-schema/type "uri")))


;; FIXME: Replace this spec with one that enforces the URL grammar.
(s/def ::url
  (-> (st/spec ::nonblank-string)
      (assoc :name "URL"
             :json-schema/description "Uniform Resource Locator"
             :json-schema/type "string")))


(s/def ::kebab-identifier
  (-> (st/spec (s/and string? #(re-matches #"^[a-z][a-z0-9]*(-[a-z0-9]+)*$" %)))
      (assoc :name "kebab-identifier"
             :json-schema/description "string consisting of lowercased words separated by dashes"
             :json-schema/type "string")))


(s/def ::kebab-identifier-keyword
  (-> (st/spec (s/and keyword? #(re-matches #"^:[a-z]+(-[a-z0-9]+)*$" (str %))))
      (assoc :name "kebab-identifier-keyword"
             :json-schema/description "keyword consisting of lowercased words separated by dashes"
             :json-schema/type "string")))


(s/def ::identifier
  (-> (st/spec (s/and string? #(re-matches #"^[a-z0-9]+(-[a-z0-9]+)*$" %)))
      (assoc :name "identifier"
             :json-schema/description "string consisting of words of lowercase letters and digits separated by single dashes"
             :json-schema/type "string")))


(s/def ::resource-type-keyword
  (-> (st/spec ::kebab-identifier-keyword)
      (assoc :name "resource type keyword"
             :json-schema/description "resource type as keyword (kebab case)"
             :json-schema/type "string")))


(def email-regex #"^[a-zA-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")

(defn email? [s] (re-matches email-regex s))

(s/def ::email
  (-> (st/spec (s/and string? email?))
      (assoc :name "email"
             :json-schema/description "valid email address"
             :json-schema/type "string")))


(def mimetype-regex #"[a-zA-Z0-9][a-zA-Z0-9!#$&^_-]{0,126}/[a-zA-Z0-9][a-zA-Z0-9!#$&^_-]{0,126}")

(defn mimetype? [s] (re-matches mimetype-regex s))

(s/def ::mimetype
  (-> (st/spec (s/and string? mimetype?))
      (assoc :name "mimetype"
             :json-schema/description "Multipurpose Internet Mail Extensions (MIME) type"
             :json-schema/type "string")))

;;
;; A resource href is the concatenation of a resource type and resource identifier separated
;; with a slash.  The later part is optional for singleton resources like the cloud-entry-point.
;;

(def resource-href-regex #"^[a-z]([a-z-]*[a-z])?(/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?)?$")

(s/def ::resource-href
  (-> (st/spec (s/and string? #(re-matches resource-href-regex %)))
      (assoc :name "resource href"
             :json-schema/description "concatenation of a resource type and resource identifier separated with a slash"
             :json-schema/type "resource-id")))
