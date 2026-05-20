 (ns com.sixsq.nuvla.server.resources.mec.app-package-subscription
   "MEC 010-2 application package subscription resources.
 
   Provides durable package subscription CRUD and notification helpers for the
   package-management tree under `app_pkgm/v1`."
   (:require
     [clojure.spec.alpha :as s]
     [clojure.string :as str]
     [clojure.tools.logging :as log]
     [com.sixsq.nuvla.auth.acl-resource :as a]
     [com.sixsq.nuvla.auth.utils :as auth]
     [com.sixsq.nuvla.server.resources.common.crud :as crud]
     [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
     [com.sixsq.nuvla.server.resources.common.utils :as u]
     [com.sixsq.nuvla.server.resources.mec.query-filter :as qf]
     [com.sixsq.nuvla.server.resources.resource-metadata :as md]
     [com.sixsq.nuvla.server.resources.spec.mec-package-subscription :as mec-package-subscription]
     [com.sixsq.nuvla.server.util.metadata :as gen-md]
     [com.sixsq.nuvla.server.util.response :as r]))
 
 (def ^:const api-resource-type "subscription")
 (def ^:const resource-type "mec-package-subscription")
 (def ^:const collection-type "mec-package-subscription-collection")
 (def ^:private default-base-path "/mec/mm1/app_pkgm/v1")
 
 (def collection-acl {:query ["group/nuvla-user"]
                      :add   ["group/nuvla-user"]})
 
 (def subscription-types
   #{"AppPackageOnBoardingNotification"
     "AppPackageStateChangeNotification"})

(def ^:private legacy-subscription-type-aliases
  {"AppPackageOnBoardingSubscription" "AppPackageOnBoardingNotification"})

(def accepted-subscription-types
  (into subscription-types (keys legacy-subscription-type-aliases)))

(def ^:private public-subscription-type-aliases
  {"AppPackageOnBoardingNotification" "AppPackageOnBoardingSubscription"})

 (def ^:private operational-states #{"ENABLED" "DISABLED"})
 (def ^:private onboarding-states #{"CREATED" "PROCESSING" "ONBOARDED" "FAILED"})

(defn canonical-subscription-type
  [subscription-type]
  (get legacy-subscription-type-aliases subscription-type subscription-type))

(defn public-subscription-type
  [subscription-type]
  (get public-subscription-type-aliases subscription-type subscription-type))
 
 (defn- request-base-path
   [request]
   (or (:mec/base-path request) default-base-path))
 
 (defn- subscription-authn
   [request]
   (or (:nuvla/authn request)
       auth/internal-identity))
 
 (defn- subscription-owner
   [request]
   (or (auth/current-active-claim request)
       (auth/current-user-id request)
       "internal"))
 
 (defn problem-details
   [type title status & {:keys [detail instance]}]
   {:type     (or type "about:blank")
    :title    title
    :status   status
    :detail   (or detail title)
    :instance instance})
 
 (defn validation-error
   [detail]
   (problem-details
     "https://docs.nuvla.io/mec/errors/validation-error"
     "Bad Request"
     400
     :detail detail))
 
 (defn conflict-error
   [detail]
   (problem-details
     "https://docs.nuvla.io/mec/errors/conflict"
     "Conflict"
     409
     :detail detail))
 
 (defn not-found-error
   [resource-id]
   (problem-details
     "https://docs.nuvla.io/mec/errors/not-found"
     "Resource Not Found"
     404
     :detail (str "Package subscription " resource-id " not found")
     :instance resource-id))
 
 (defn json-response-status
   [body status]
   (cond-> {:status status}
     (some? body) (assoc :body body)))
 
 (defn normalize-callback-uri
   [callback-uri]
   (some-> callback-uri str str/trim))
 
 (defn canonicalize-filter
   [filter-opts]
   (some->> filter-opts
            (remove (comp nil? val))
            (into {})))
 
 (defn normalize-app-pkg-filter
   [filter-opts]
   (cond-> {}
     (or (:appPkgId filter-opts) (:app-pkg-id filter-opts))
     (assoc :app-pkg-id (or (:appPkgId filter-opts)
                            (:app-pkg-id filter-opts)))
     (or (:appDId filter-opts) (:app-d-id filter-opts))
     (assoc :app-d-id (or (:appDId filter-opts)
                          (:app-d-id filter-opts)))
     (or (:appName filter-opts) (:app-name filter-opts))
     (assoc :app-name (or (:appName filter-opts)
                          (:app-name filter-opts)))
     (or (:operationalState filter-opts) (:operational-state filter-opts))
     (assoc :operational-state (or (:operationalState filter-opts)
                                   (:operational-state filter-opts)))
     (or (:onboardingState filter-opts) (:onboarding-state filter-opts))
     (assoc :onboarding-state (or (:onboardingState filter-opts)
                                  (:onboarding-state filter-opts)))))
 
 (defn api-id->resource-id
   [subscription-id]
   (let [[_ uuid] (u/parse-id subscription-id)]
     (when uuid
       (str resource-type "/" uuid))))
 
 (defn resource-id->api-id
   [subscription-id]
   (let [[_ uuid] (u/parse-id subscription-id)]
     (when uuid
       (str api-resource-type "/" uuid))))
 
 (defn resource->api-subscription
   [resource]
   (when resource
     (update resource :id resource-id->api-id)))
 
 (defn create-subscription
   [subscription-type callback-uri filter-opts user-id api-base-path]
   (let [subscription-id (str api-resource-type "/" (java.util.UUID/randomUUID))
         now             (str (java.time.Instant/now))]
     (cond-> {:id                subscription-id
              :subscription-type subscription-type
              :callback-uri      callback-uri
              :created           now
              :updated           now
              :owner             user-id
              :active            true
              :api-base-path     api-base-path}
       (seq filter-opts)
       (assoc :app-pkg-filter filter-opts))))
 
 (defn validate-subscription
   [subscription]
   (let [callback-uri (:callback-uri subscription)
         filter       (:app-pkg-filter subscription)
         errors       (cond-> []
                       (not (contains? accepted-subscription-types (:subscription-type subscription)))
                        (conj {:field :subscription-type :message "Invalid package subscription type"})
                        (or (not (string? callback-uri))
                            (nil? (re-matches #"https?://.*" callback-uri)))
                        (conj {:field :callback-uri :message "Callback URI must be an HTTP(S) URL"})
                        (and (:operational-state filter)
                             (not (contains? operational-states (:operational-state filter))))
                        (conj {:field :app-pkg-filter/operational-state :message "Invalid operational state"})
                        (and (:onboarding-state filter)
                             (not (contains? onboarding-states (:onboarding-state filter))))
                        (conj {:field :app-pkg-filter/onboarding-state :message "Invalid onboarding state"}))]
     (if (empty? errors)
       {:valid? true}
       {:valid? false
        :errors errors})))
 
 (def resource-metadata
   (-> (gen-md/generate-metadata ::ns ::mec-package-subscription/schema)
       (assoc :type-uri resource-type
              :name resource-type)))
 
 (defn initialize
   []
   (std-crud/initialize resource-type ::mec-package-subscription/schema)
   (md/register resource-metadata))
 
 (def validate-fn (u/create-spec-validation-fn ::mec-package-subscription/schema))
 
 (defmethod crud/validate resource-type
   [resource]
   (validate-fn resource))
 
 (defmethod crud/add-acl resource-type
   [resource request]
   (a/add-acl resource request))
 
 (def add-impl (std-crud/add-fn resource-type collection-acl resource-type))
 
 (defmethod crud/add resource-type
   [request]
   (add-impl request))
 
 (def retrieve-impl (std-crud/retrieve-fn resource-type))
 
 (defmethod crud/retrieve resource-type
   [request]
   (retrieve-impl request))
 
 (def delete-impl (std-crud/delete-fn resource-type))
 
 (defmethod crud/delete resource-type
   [request]
   (delete-impl request))
 
 (def query-impl (std-crud/query-fn resource-type collection-acl collection-type))
 
 (defmethod crud/query resource-type
   [request]
   (query-impl request))
 
 (defn get-active-subscriptions-for-type
   [subscriptions subscription-type]
   (filterv #(and (:active %)
                 (= (canonical-subscription-type subscription-type)
                    (canonical-subscription-type (:subscription-type %))))
            subscriptions))
 
 (defn matches-app-pkg-filter?
   [subscription app-pkg]
   (let [filter (:app-pkg-filter subscription)]
     (or (empty? filter)
         (and (or (nil? (:app-pkg-id filter))
                  (= (:app-pkg-id filter) (:app-pkg-id app-pkg)))
              (or (nil? (:app-d-id filter))
                  (= (:app-d-id filter) (:app-d-id app-pkg)))
              (or (nil? (:app-name filter))
                  (= (:app-name filter) (:app-name app-pkg)))
              (or (nil? (:operational-state filter))
                  (= (:operational-state filter) (:operational-state app-pkg)))
              (or (nil? (:onboarding-state filter))
                  (= (:onboarding-state filter) (:onboarding-state app-pkg)))))))
 
 (defn- subscription-link
   [subscription]
   (str (or (:api-base-path subscription) default-base-path)
        "/subscriptions/"
        (:id subscription)))
 
 (defn- app-package-link
   [subscription app-pkg]
   (str (or (:api-base-path subscription) default-base-path)
        "/app_packages/"
        (:app-pkg-id app-pkg)))
 
 (defn build-app-package-onboarding-notification
   [subscription app-pkg]
   {:notification-type "AppPackageOnBoardingNotification"
    :notification-id   (str "notification/" (java.util.UUID/randomUUID))
    :subscription-id   (:id subscription)
    :app-pkg-id        (:app-pkg-id app-pkg)
    :app-d-id          (:app-d-id app-pkg)
    :app-name          (:app-name app-pkg)
    :operational-state (:operational-state app-pkg)
    :onboarding-state  (:onboarding-state app-pkg)
    :timestamp         (str (java.time.Instant/now))
    :_links            {:subscription {:href (subscription-link subscription)}
                        :app-package  {:href (app-package-link subscription app-pkg)}}})
 
 (defn build-app-package-state-change-notification
   [subscription app-pkg change-type previous-state]
   {:notification-type "AppPackageStateChangeNotification"
    :notification-id   (str "notification/" (java.util.UUID/randomUUID))
    :subscription-id   (:id subscription)
    :app-pkg-id        (:app-pkg-id app-pkg)
    :app-d-id          (:app-d-id app-pkg)
    :app-name          (:app-name app-pkg)
    :operational-state (:operational-state app-pkg)
    :onboarding-state  (:onboarding-state app-pkg)
    :change-type       change-type
    :previous-state    previous-state
    :timestamp         (str (java.time.Instant/now))
    :_links            {:subscription {:href (subscription-link subscription)}
                        :app-package  {:href (app-package-link subscription app-pkg)}}})
 
 (defn subscription-response
   [resource route-base-path]
   (when resource
     (cond-> {:id               (some-> (:id resource) resource-id->api-id)
             :subscriptionType (some-> (:subscription-type resource) public-subscription-type)
              :callbackUri      (:callback-uri resource)
              :_links           {:self {:href (str route-base-path
                                                   "/subscriptions/"
                                                   (resource-id->api-id (:id resource)))}}}
       (seq (:app-pkg-filter resource))
       (assoc :appPkgFilter
              (let [filter (:app-pkg-filter resource)]
                (cond-> {}
                  (:app-pkg-id filter) (assoc :appPkgId (:app-pkg-id filter))
                  (:app-d-id filter) (assoc :appDId (:app-d-id filter))
                  (:app-name filter) (assoc :appName (:app-name filter))
                  (:operational-state filter) (assoc :operationalState (:operational-state filter))
                  (:onboarding-state filter) (assoc :onboardingState (:onboarding-state filter))))))))
 
 (defn query-user-subscriptions
   [request]
   (-> {:params      {:resource-name resource-type}
        :cimi-params {:last 1000}
        :nuvla/authn (subscription-authn request)}
       crud/query
       :body
       :resources))
 
 (defn- retrieve-subscription-resource
   [public-id]
   (some-> public-id
           api-id->resource-id
           crud/retrieve-by-id-as-admin1))
 
 (defn- duplicate-active-subscription
   [request subscription-type callback-uri filter-opts user-id]
   (let [callback-uri* (normalize-callback-uri callback-uri)
        filter-opts*  (canonicalize-filter filter-opts)
        subscription-type* (canonical-subscription-type subscription-type)]
     (some (fn [existing]
             (and (:active existing)
                  (= user-id (:owner existing))
                 (= subscription-type* (canonical-subscription-type (:subscription-type existing)))
                  (= callback-uri* (normalize-callback-uri (:callback-uri existing)))
                  (= filter-opts* (canonicalize-filter (:app-pkg-filter existing)))))
           (query-user-subscriptions request))))
 
 (defn create-subscription-handler
   [request]
   (try
     (let [body              (:body request)
          subscription-type (canonical-subscription-type (:subscriptionType body))
           callback-uri      (:callbackUri body)
           filter-opts       (normalize-app-pkg-filter (:appPkgFilter body))
           user-id           (subscription-owner request)
           api-base-path     (request-base-path request)]
       (when-not (contains? subscription-types subscription-type)
         (throw (ex-info "Invalid package subscription type"
                         {:status 400
                          :subscription-type subscription-type})))
       (when (duplicate-active-subscription request subscription-type callback-uri filter-opts user-id)
         (throw (ex-info "An active MEC package subscription with the same callback URI and filter already exists"
                         {:status 409})))
       (let [preview    (create-subscription subscription-type
                                            (normalize-callback-uri callback-uri)
                                            filter-opts
                                            user-id
                                            api-base-path)
             resource   (cond-> {:subscription-type subscription-type
                                 :callback-uri      (normalize-callback-uri callback-uri)
                                 :owner             user-id
                                 :active            true
                                 :api-base-path     api-base-path}
                          (seq filter-opts) (assoc :app-pkg-filter filter-opts))
             validation (validate-subscription preview)]
         (when-not (:valid? validation)
           (throw (ex-info "Invalid package subscription"
                           {:status 400
                            :errors (:errors validation)})))
         (let [create-response (crud/add {:params      {:resource-name resource-type}
                                          :body        resource
                                          :nuvla/authn (subscription-authn request)})
               resource-id     (get-in create-response [:body :resource-id])
               persisted       (crud/retrieve-by-id-as-admin1 resource-id)]
           (log/info "Created package subscription" resource-id "for user" user-id)
           (json-response-status (subscription-response persisted api-base-path) 201))))
     (catch clojure.lang.ExceptionInfo e
       (let [data (ex-data e)]
         (log/error e "Failed to create package subscription")
         (json-response-status
           ((case (or (:status data) 400)
              409 conflict-error
              validation-error)
            (ex-message e))
           (or (:status data) 400))))
     (catch Exception e
       (log/error e "Unexpected error creating package subscription")
       (json-response-status (problem-details "about:blank"
                                              "Internal Server Error"
                                              500
                                              :detail (ex-message e))
                             500))))
 
 (defn list-subscriptions-handler
   [request]
   (try
     (let [route-base-path   (request-base-path request)
           active-user-subs  (->> (query-user-subscriptions request)
                                  (filter :active)
                                  (mapv #(subscription-response % route-base-path)))]
       (r/json-response (qf/process-query active-user-subs
                                          (merge (:params request)
                                                 {:base-uri (str route-base-path "/subscriptions")}))))
     (catch Exception e
       (log/error e "Failed to list package subscriptions")
       (json-response-status (problem-details "about:blank"
                                              "Internal Server Error"
                                              500
                                              :detail (ex-message e))
                             500))))
 
 (defn get-subscription-handler
   [request]
   (try
     (let [subscription-id (get-in request [:params :id])
           user-id         (subscription-owner request)
           route-base-path (request-base-path request)
           sub             (retrieve-subscription-resource subscription-id)]
       (cond
         (or (nil? sub) (false? (:active sub)))
         (json-response-status (not-found-error subscription-id) 404)
 
         (not= (:owner sub) user-id)
         (json-response-status (problem-details
                                 "https://docs.nuvla.io/mec/errors/forbidden"
                                 "Access Forbidden"
                                 403
                                 :detail "You do not have permission to access this package subscription"
                                 :instance subscription-id)
                               403)
 
         :else
         (r/json-response (subscription-response sub route-base-path))))
     (catch Exception e
       (log/error e "Failed to get package subscription")
       (json-response-status (problem-details "about:blank"
                                              "Internal Server Error"
                                              500
                                              :detail (ex-message e))
                             500))))
 
 (defn delete-subscription-handler
   [request]
   (try
     (let [subscription-id (get-in request [:params :id])
           user-id         (subscription-owner request)
           sub             (retrieve-subscription-resource subscription-id)]
       (cond
         (or (nil? sub) (false? (:active sub)))
         (json-response-status (not-found-error subscription-id) 404)
 
         (not= (:owner sub) user-id)
         (json-response-status (problem-details
                                 "https://docs.nuvla.io/mec/errors/forbidden"
                                 "Access Forbidden"
                                 403
                                 :detail "You do not have permission to delete this package subscription"
                                 :instance subscription-id)
                               403)
 
         :else
         (do
           (let [resource-id (api-id->resource-id subscription-id)]
             (crud/delete {:params      (u/id->request-params resource-id)
                           :nuvla/authn (subscription-authn request)})
             (log/info "Deleted package subscription" resource-id))
           (json-response-status nil 204))))
     (catch Exception e
       (log/error e "Failed to delete package subscription")
       (json-response-status (problem-details "about:blank"
                                              "Internal Server Error"
                                              500
                                              :detail (ex-message e))
                             500))))
