(ns sixsq.nuvla.server.resources.spec.common
  "Spec definitions for common types used in CIMI resources."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.spec.common-operation :as cimi-common-operation]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::id
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "id"
             :json-schema/name "id"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "identifier"
             :json-schema/description "unique resource identifier"
             :json-schema/section "meta"
             :json-schema/order 0
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::resource-type
  (-> (st/spec ::cimi-core/uri)
      (assoc :name "resource-type"
             :json-schema/name "resource-type"
             :json-schema/type "uri"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "resource URI"
             :json-schema/description "URI for resource type"
             :json-schema/section "meta"
             :json-schema/order 1
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::created
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "created"
             :json-schema/name "created"
             :json-schema/type "date-time"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "created"
             :json-schema/description "creation timestamp (UTC) for resource"
             :json-schema/section "meta"
             :json-schema/order 2
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::updated
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "updated"
             :json-schema/name "updated"
             :json-schema/type "date-time"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "updated"
             :json-schema/description "latest resource update timestamp (UTC)"
             :json-schema/section "meta"
             :json-schema/order 3
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::name
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "name"
             :json-schema/name "name"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true
             :json-schema/searchable true

             :json-schema/display-name "name"
             :json-schema/description "short, human-readable name for resource"
             :json-schema/section "meta"
             :json-schema/order 4
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::description
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "description"
             :json-schema/name "description"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true
             :json-schema/searchable true

             :json-schema/display-name "description"
             :json-schema/description "human-readable description of resource"
             :json-schema/section "meta"
             :json-schema/order 5
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::parent
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "parent"
             :json-schema/name "parent"
             :json-schema/type "uri"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "parent"
             :json-schema/description "reference to parent resource"
             :json-schema/section "meta"
             :json-schema/order 6
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::resource-metadata
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "resource-metadata"
             :json-schema/name "resource-metadata"
             :json-schema/type "uri"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "resource metadata"
             :json-schema/description "reference to the resource's metadata"
             :json-schema/section "meta"
             :json-schema/order 7
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::href
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "href"
             :json-schema/description "reference to another resource"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::resource-link
  (-> (st/spec (s/keys :req-un [::href]))
      (assoc :name "resourceLink"
             :json-schema/name "resourceLink"
             :json-schema/type "map"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "resourceLink"
             :json-schema/description "map containing a reference (href) to a resource"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::resource-links
  (-> (st/spec (s/coll-of ::resource-link :min-count 1))
      (assoc :name "resourceLinks"
             :json-schema/name "resourceLinks"
             :json-schema/type "array"
             :json-schema/provider-mandatory false
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "resourceLinks"
             :json-schema/description "list of resourceLinks"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::operation
  (-> (st/spec (su/only-keys :req-un [::cimi-common-operation/href
                                      ::cimi-common-operation/rel]))
      (assoc :name "operation"
             :json-schema/name "operation"
             :json-schema/type "map"
             :json-schema/provider-mandatory false
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "operation"
             :json-schema/description "operation definition (name, URL) for a resource"
             :json-schema/section "meta"
             :json-schema/order 0
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::operations
  (-> (st/spec (s/coll-of ::operation :min-count 1))
      (assoc :name "operations"
             :json-schema/name "operations"
             :json-schema/type "array"
             :json-schema/provider-mandatory false
             :json-schema/required false
             :json-schema/editable true
             :json-schema/indexed false

             :json-schema/display-name "operations"
             :json-schema/description "list of resource operations"
             :json-schema/section "meta"
             :json-schema/order 0
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::tags
  (-> (st/spec (s/coll-of string? :min-count 1 :into #{}))
      (assoc :name "tags"
             :json-schema/name "tags"
             :json-schema/type "array"
             :json-schema/provider-mandatory false
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "tags"
             :json-schema/description "client defined tags of the resource"
             :json-schema/section "meta"
             :json-schema/order 15
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::acl (st/spec ::acl-resource/acl))


(def ^:const common-attrs
  "clojure.spec/keys specification (as a map) for common CIMI attributes
   for regular resources"
  {:req-un [::id
            ::resource-type
            ::created
            ::updated
            ::acl]
   :opt-un [::name
            ::description
            ::tags
            ::parent
            ::resource-metadata
            ::operations]})


(def ^:const create-attrs
  "clojure.spec/keys specification (as a map) for common CIMI attributes
   for the 'create' resources used when creating resources from a template.
   This applies to the create wrapper and not the embedded resource
   template!"
  {:req-un [::resource-type]
   :opt-un [::name
            ::description
            ::created
            ::updated
            ::tags
            ::parent
            ::resource-metadata
            ::operations
            ::acl]})


(def ^:const template-attrs
  "The clojure.spec/keys specification (as a map) for common CIMI attributes
   for the resource templates that are embedded in 'create' resources. Although
   these may be added to the templates (usually by reference), they will have
   no affect on the created resource."
  {:opt-un [::resource-type
            ::name
            ::description
            ::created
            ::updated
            ::tags
            ::parent
            ::resource-metadata
            ::operations
            ::acl]})
