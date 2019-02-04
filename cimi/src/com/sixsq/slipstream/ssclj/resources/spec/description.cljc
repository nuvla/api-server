(ns com.sixsq.slipstream.ssclj.resources.spec.description
  "Spec definitions for the descriptions of resources. These descriptions
   provide information to clients to allow them to understand the contents
   of resources."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

;;
;; parameter descriptions for providing help information to clients
;; these definitions are the 'cimi.desc' namespace
;;

(s/def ::displayName ::cimi-core/nonblank-string)
(s/def ::category ::cimi-core/nonblank-string)
(s/def ::description ::cimi-core/nonblank-string)
(s/def ::type #{"string" "boolean" "int" "float" "timestamp" "enum" "password" "hidden" "map" "list"})
(s/def ::mandatory boolean?)
(s/def ::readOnly boolean?)
(s/def ::order nat-int?)
(s/def ::enum (s/coll-of ::cimi-core/nonblank-string :min-count 1))
(s/def ::instructions ::cimi-core/nonblank-string)

;;
;; This field is intended as a hint to browser (and other visual)
;; clients on autofilling values.  If not specified, clients should
;; default to a value of 'off'; otherwise, the value should be used.
;;
;; The HTML5 specification provides an exhaustive list of the values
;; and how the browsers should behave.  Support is not perfect, but
;; should be good enough to provide reasonable behavior.
;;
;; https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#autofill
;;
(s/def ::autocomplete ::cimi-core/nonblank-string)


(s/def ::parameter-description
  (su/only-keys :req-un [::type]
                :opt-un [::displayName
                         ::category
                         ::description
                         ::mandatory
                         ::readOnly
                         ::order
                         ::enum
                         ::autocomplete
                         ::instructions]))

(s/def ::resource-description
  (s/every (s/or :acl (s/tuple #{:acl} ::cimi-common/acl)
                 :desc (s/tuple keyword? ::parameter-description))))

