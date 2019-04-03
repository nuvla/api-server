(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute
  "schema definitions for the 'attributes' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as value-scope]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::cimi-core/token)

(s/def ::type #{"boolean" "dateTime" "duration" "integer" "string" "ref" "double" "URI"
                "map" "Array" "Any"})

(s/def ::provider-mandatory boolean?)

(s/def ::consumer-mandatory boolean?)

(s/def ::mutable boolean?)

(s/def ::consumer-writable boolean?)

(s/def ::template-mutable boolean?)


;;
;; the following attributes are extensions to the standard that are
;; useful for rendering forms for browser-based clients
;;

(s/def ::display-name ::cimi-core/nonblank-string)

(s/def ::description ::cimi-core/nonblank-string)

(s/def ::help ::cimi-core/nonblank-string)

(s/def ::group #{"metadata" "body" "operations" "acl"})

(s/def ::category ::cimi-core/nonblank-string)

(s/def ::order nat-int?)

(s/def ::hidden boolean?)

(s/def ::sensitive boolean?)

(s/def ::lines pos-int?)


;;
;; this attribute helps the interaction with elasticsearch
;; to prevent unwanted indexing of attributes
;;

(s/def ::indexed boolean?)


;;
;; NOTE: The CIMI specification states that the :type attributes will
;; not be present for standard CIMI attributes.  This implementation
;; makes :type mandatory for all attribute descriptions.  This makes
;; life easier for clients.
;;
(s/def ::attribute (su/only-keys :req-un [::name
                                          ::type
                                          ::provider-mandatory
                                          ::consumer-mandatory
                                          ::mutable
                                          ::consumer-writable]
                                 :opt-un [::display-name
                                          ::description
                                          ::help
                                          ::group
                                          ::category
                                          ::order
                                          ::hidden
                                          ::sensitive
                                          ::lines
                                          ::template-mutable
                                          ::indexed

                                          ::value-scope/value-scope]))


;; FIXME: This function shouldn't be necessary!
;; There is a problem when using the ::value-scope spec directly in the
;; s/map-of expression in st/spec.  Validation throws an exception when
;; trying to validate against single-value or collection-item.  Hiding
;; the details behind this function works, but clearly isn't ideal for
;; error reporting. The reason for the problem needs to be determined
;; and either worked around or fixed.
(defn valid-attribute?
  [x]
  (s/valid? ::attribute x))


(s/def ::attributes
  (st/spec {:spec                (s/coll-of valid-attribute? :min-count 1 :type vector?)
            :json-schema/indexed false}))
