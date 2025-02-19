(ns com.sixsq.nuvla.migration.api-client
  (:require [jsonista.core :as j]
            [clojure.string :as str]
            [environ.core :as env]
            [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [com.sixsq.nuvla.server.app.params :as p])
  (:import (clojure.lang ExceptionInfo)))

(def envs
  {:prod    {:base-uri         "https://nuvla.io/api"
             :username-env-var :prod-username
             :password-env-var :prod-password}
   :preprod {:base-uri         "https://preprod.nuvla.io/api"
             :username-env-var :preprod-username
             :password-env-var :preprod-password}
   :dev-alb {:base-uri         "https://159.100.246.22/api"
             :username-env-var :dev-alb-username
             :password-env-var :dev-alb-password}
   :local   {:base-uri         "http://localhost:8200/api"
             :username-env-var :local-username
             :password-env-var :local-password}})

(defn base-resource-uri
  [resource-type]
  (str p/service-context resource-type))

(def cookie-store (cookies/cookie-store))

(defonce session (atom nil))

(defn reset-session
  []
  (reset! session nil))

(defn user-session
  [env-key]
  (let [{:keys [base-uri username-env-var password-env-var]} (get envs env-key)
        username (env/env username-env-var)
        pwd      (env/env password-env-var)
        _        (prn base-uri username pwd)
        response (http/post (str base-uri "/session")
                            {:headers       {"content-type" "application/json"}
                             :body          (j/write-value-as-string
                                              {:template {:username username
                                                          :password pwd
                                                          :href     "session-template/password"}})
                             :cookie-store  cookie-store
                             :cookie-policy :standard
                             :insecure?     true})
        session  (-> response :body (j/read-value j/keyword-keys-object-mapper))]
    (when-not (= 201 (:status session))
      (throw (ex-info "An error occurred creating session"
                      {:status  (:status session)
                       :message (:message session)})))
    {:session-id (:resource-id session)
     :base-uri   base-uri}))

(defn switch-group
  [{:keys [base-uri session-id] :as session-data} group extended]
  (try
    (let [response (http/post (str base-uri "/" session-id "/switch-group")
                              {:headers       {"content-type" "application/json"}
                               :body          #_"{\"claim\":\"group\\/ekinops-dev\",\"extended\":true}"
                               (j/write-value-as-string
                                 {:claim    group
                                  :extended extended})
                               :cookie-store  cookie-store
                               :cookie-policy :standard
                               :insecure?     true})
          session  (-> response :body (j/read-value j/keyword-keys-object-mapper))]
      (assoc session-data :session session))
    (catch Exception ex (prn ex))))

(defn ensure-admin-session
  [env-key]
  (when-not @session
    (reset! session (-> (user-session env-key)
                        (switch-group "group/nuvla-admin" true)))))

(defn data
  ([opts]
   (data @session opts))
  ([{:keys [base-uri]} {:keys [filter from to granularity dataset]}]
   (let [response (http/patch (str base-uri "/nuvlabox/data")
                              {:headers      {"content-type" "application/json"
                                              "bulk"         "true"}
                               :body         (j/write-value-as-string
                                               (cond->
                                                 {:dataset     dataset
                                                  :from        from
                                                  :to          to
                                                  :granularity granularity}
                                                 filter (assoc :filter filter)))
                               :cookie-store cookie-store
                               :insecure?    true})
         data     (-> response :body (j/read-value j/keyword-keys-object-mapper))]
     data)))

(defn resources
  ([resource-type opts]
   (resources @session resource-type opts))
  ([{:keys [base-uri]} resource-type {:keys [filter last select orderby]}]
   (let [response  (http/put (str base-uri "/" resource-type)
                             {:headers      {"content-type" "application/json"}
                              :form-params  (cond-> {}
                                                    filter (assoc :filter filter)
                                                    last (assoc :last last)
                                                    select (assoc :select (str/join "," (map name select)))
                                                    orderby (assoc :orderby orderby))
                              :cookie-store cookie-store
                              :insecure?    true})
         resources (-> response :body (j/read-value j/keyword-keys-object-mapper))]
     resources)))

(defn get-resource-by-id
  ([resource-id]
   (get-resource-by-id @session resource-id))
  ([{:keys [base-uri]} resource-id]
   (try
     (let [response (http/get (str base-uri "/" resource-id)
                              {:headers      {"content-type" "application/json"}
                               :cookie-store cookie-store
                               :insecure?    true})
           resource (-> response :body (j/read-value j/keyword-keys-object-mapper))]
       resource)
     (catch ExceptionInfo ex
       (let [status (:status (ex-data ex))]
         (when (not= status 404)
           (throw ex)))))))

(defn edges
  ([opts]
   (edges @session opts))
  ([session opts]
   (resources session "nuvlabox" opts)))

(defn deployment
  ([opts]
   (deployment @session opts))
  ([session opts]
   (resources session "deployment" opts)))

(defn deployment-groups
  ([opts]
   (deployment-groups @session opts))
  ([session opts]
   (resources session "deployment-set" opts)))

(defn infrastructure-services
  ([opts]
   (infrastructure-services @session opts))
  ([session opts]
   (resources session "infrastructure-service" opts)))

(defn edge-heartbeat
  ([edge-id]
   (edge-heartbeat @session edge-id))
  ([{:keys [base-uri]} edge-id]
   (let [response (http/post (str base-uri "/" edge-id "/heartbeat")
                             {:headers      {"content-type" "application/json"}
                              :cookie-store cookie-store
                              :insecure?    true})
         resp     (-> response :body (j/read-value j/keyword-keys-object-mapper))]
     resp)))

(defn edge-set-offline
  ([edge-id]
   (edge-set-offline @session edge-id))
  ([{:keys [base-uri]} edge-id]
   (let [response (http/post (str base-uri "/" edge-id "/set-offline")
                             {:headers      {"content-type" "application/json"}
                              :cookie-store cookie-store
                              :insecure?    true})
         resp     (-> response :body (j/read-value j/keyword-keys-object-mapper))]
     resp)))

(defn module
  ([opts]
   (module @session opts))
  ([session opts]
   (resources session "module" opts)))

(defn module-version
  ([module-id version-idx]
   (module-version @session module-id version-idx))
  ([session module-id version-idx]
   (let [{:keys [versions]} (get-resource-by-id session module-id)
         module-version-id (when (<= 0 version-idx (dec (count versions)))
                             (:href (nth versions version-idx)))]
     (get-resource-by-id session module-version-id))))

(defn patch-resource
  ([resource-id data]
   (patch-resource @session resource-id data))
  ([{:keys [base-uri]} resource-id data]
   (let [response (http/put (str base-uri "/" resource-id)
                            {:headers      {"content-type" "application/json"}
                             :body         (j/write-value-as-string data)
                             :cookie-store cookie-store
                             :insecure?    true})
         resp     (-> response :body (j/read-value j/keyword-keys-object-mapper))]
     resp)))

(comment
  (let [session (-> (user-session :prod)
                    (switch-group "group/ekinops-dev" true))]
    (prn (data session)))
  (data {:base-uri (-> envs :prod :base-uri)})
  (prn (cookies/get-cookies cookie-store)))
