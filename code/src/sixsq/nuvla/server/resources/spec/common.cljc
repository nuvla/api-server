(ns sixsq.nuvla.server.resources.spec.common
  "Spec definitions for common types used in CIMI resources."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.spec.common-operation :as cimi-common-operation]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [spec-tools.core :as st]))


(s/def ::href
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "href"
             :json-schema/description "reference to another resource")))


(s/def ::resource-link
  (-> (st/spec (s/keys :req-un [::href]))
      (assoc :name "resource-link"
             :json-schema/type "map"
             :json-schema/display-name "resource link"
             :json-schema/description "map containing a reference (href) to a resource")))


(s/def ::resource-links
  (-> (st/spec (s/coll-of ::resource-link :min-count 1))
      (assoc :name "resource-links"
             :json-schema/type "array"
             :json-schema/display-name "resource links"
             :json-schema/description "list of resource links")))

;;
;; core meta
;;

(s/def ::id
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "id"
             :json-schema/display-name "identifier"
             :json-schema/description "unique resource identifier"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 0)))


(s/def ::parent
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "parent"
             :json-schema/description "reference to parent resource"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 1)))


(s/def ::resource-type
  (-> (st/spec ::cimi-core/uri)
      (assoc :name "resource-type"
             :json-schema/display-name "resource type"
             :json-schema/description "resource type identifier"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 2)))


(s/def ::resource-metadata
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "resource-metadata"
             :json-schema/display-name "resource metadata"
             :json-schema/description "reference to the resource's metadata"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 3)))


(s/def ::created
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "created"
             :json-schema/type "date-time"
             :json-schema/description "creation timestamp (UTC) for resource"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 4)))


(s/def ::updated
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "updated"
             :json-schema/type "date-time"
             :json-schema/description "latest resource update timestamp (UTC)"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 5)))


(s/def ::name
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "name"
             :json-schema/description "short, human-readable name for resource"
             :json-schema/section "meta"

             :json-schema/fulltext true
             :json-schema/order 6)))


(s/def ::description
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "description"
             :json-schema/description "human-readable description of resource"
             :json-schema/section "meta"

             :json-schema/fulltext true
             :json-schema/order 7)))


(s/def ::tags
  (-> (st/spec (s/coll-of string? :min-count 1 :type vector? :distinct true))
      (assoc :name "tags"
             :json-schema/type "array"
             :json-schema/description "client defined tags of the resource"
             :json-schema/section "meta"

             :json-schema/fulltext true
             :json-schema/order 8)))


(s/def ::operations (-> (st/spec ::cimi-common-operation/operations)
                        (assoc :json-schema/order 9)))


(s/def ::acl (-> (st/spec ::acl-resource/acl)
                 (assoc :json-schema/order 0)))


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
