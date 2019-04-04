(ns sixsq.nuvla.server.resources.spec.acl-common
  "Common schema definitions for resource Access Control Lists (ACLs)."
  (:require
    [clojure.spec.alpha :as s]
    [spec-tools.core :as st]))


(def principal-regex #"^[a-z-]+/[a-z0-9-]+$")


(s/def ::principal
  (-> (st/spec (s/and string? #(re-matches principal-regex %)))
      (assoc :name "principal"
             :json-schema/name "principal"
             :json-schema/type "string"
             :json-schema/display-name "principal"
             :json-schema/description "unique identifier for a principal")))


(s/def ::principals
  (-> (st/spec (s/coll-of ::principal :kind vector? :distinct true))
      (assoc :name "principals"
             :json-schema/name "principals"
             :json-schema/type "array"
             :json-schema/display-name "principals"
             :json-schema/description "list of principals")))
