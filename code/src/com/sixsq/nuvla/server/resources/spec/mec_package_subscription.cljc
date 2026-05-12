 (ns com.sixsq.nuvla.server.resources.spec.mec-package-subscription
   (:require
     [clojure.spec.alpha :as s]
     [com.sixsq.nuvla.server.resources.spec.common :as common]
     [com.sixsq.nuvla.server.resources.spec.core :as core]
     [com.sixsq.nuvla.server.util.spec :as su]
     [spec-tools.core :as st]))

 (s/def ::subscription-type
   (-> (st/spec #{"AppPackageOnBoardingNotification"
                  "AppPackageStateChangeNotification"})
       (assoc :name "package subscription type"
              :json-schema/type "string"
              :json-schema/description "ETSI MEC application package notification type"
              :json-schema/order 20)))

 (s/def ::callback-uri
   (-> (st/spec (s/and string? #(re-matches #"https?://.*" %)))
       (assoc :name "callback URI"
              :json-schema/type "string"
              :json-schema/description "HTTP(S) endpoint receiving MEC package notifications"
              :json-schema/order 21)))

 (s/def ::owner
   (-> (st/spec ::core/resource-href)
       (assoc :name "owner"
              :json-schema/type "resource-id"
              :json-schema/description "owner of the MEC package subscription"
              :json-schema/order 22)))

 (s/def ::active
   (-> (st/spec boolean?)
       (assoc :name "active"
              :json-schema/type "boolean"
              :json-schema/description "whether the package subscription is active"
              :json-schema/order 23)))

 (s/def ::api-base-path
   (-> (st/spec string?)
       (assoc :name "API base path"
              :json-schema/type "string"
              :json-schema/description "route prefix used when creating package links"
              :json-schema/order 24)))

 (s/def ::app-pkg-id
   (-> (st/spec ::core/resource-href)
       (assoc :name "application package id"
              :json-schema/type "resource-id"
              :json-schema/description "specific application package to match"
              :json-schema/order 30)))

 (s/def ::app-d-id
   (-> (st/spec ::core/resource-href)
       (assoc :name "application descriptor id"
              :json-schema/type "resource-id"
              :json-schema/description "application descriptor identifier to match"
              :json-schema/order 31)))

 (s/def ::app-name
   (-> (st/spec string?)
       (assoc :name "app name"
              :json-schema/type "string"
              :json-schema/description "application package name filter"
              :json-schema/order 32)))

 (s/def ::operational-state
   (-> (st/spec #{"ENABLED" "DISABLED"})
       (assoc :name "operational state"
              :json-schema/type "string"
              :json-schema/description "application package operational state filter"
              :json-schema/order 33)))

 (s/def ::onboarding-state
   (-> (st/spec #{"CREATED" "PROCESSING" "ONBOARDED" "FAILED"})
       (assoc :name "onboarding state"
              :json-schema/type "string"
              :json-schema/description "application package onboarding state filter"
              :json-schema/order 34)))

 (s/def ::app-pkg-filter
   (-> (st/spec (su/only-keys-maps {:opt-un [::app-pkg-id
                                             ::app-d-id
                                             ::app-name
                                             ::operational-state
                                             ::onboarding-state]}))
       (assoc :name "application package filter"
              :json-schema/type "map"
              :json-schema/description "filter criteria for application package notifications"
              :json-schema/order 35)))

 (def attributes
   {:req-un [::subscription-type
             ::callback-uri
             ::owner
             ::active]
    :opt-un [::app-pkg-filter
             ::api-base-path]})

 (s/def ::schema
   (su/only-keys-maps common/common-attrs
                      attributes))
