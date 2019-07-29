(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.util.response :as r]))


(defn generate-api-key-secret
  [deployment-id authn-info]
  (let [request-api-key {:params      {:resource-name credential/resource-type}
                         :body        {:name        (str "API credential for " deployment-id)
                                       :description (str "generated API credential for " deployment-id)
                                       :parent      deployment-id
                                       :template    {:href (str "credential-template/" cred-api-key/method)}}
                         :nuvla/authn authn-info}
        {{:keys [status resource-id secret-key]} :body :as response} (crud/add request-api-key)]
    (when (= status 201)
      {:api-key    resource-id
       :api-secret secret-key})))


(defn assoc-api-credentials
  [deployment-id authn-info]
  (try
    (if-let [api-credentials (generate-api-key-secret deployment-id authn-info)]
      (let [edit-request {:params      (u/id->request-params deployment-id)
                          :body        {:api-credentials api-credentials}
                          :nuvla/authn authn-info}
            {:keys [status] :as response} (crud/edit edit-request)]
        (when (not= status 200)
          (log/error "could not add api key/secret to" deployment-id response)))
      (log/error "could not create api key/secret for" deployment-id))
    (catch Exception e
      (log/error (str "exception when creating api key/secret for " deployment-id ": " e)))))


(defn delete-deployment-credentials
  "Attempts to delete all credentials associated with the deployment via the
   parent attribute. Exceptions are logged but otherwise ignored."
  [authn-info deployment-id]
  (try
    (let [credentials-query {:params      {:resource-name credential/resource-type}
                             :cimi-params {:filter (cimi-params-impl/cimi-filter {:filter (str "parent='" deployment-id "'")})
                                           :select ["id"]}
                             :nuvla/authn authn-info}
          credential-ids    (->> credentials-query crud/query :body :resources (map :id))]

      (doseq [credential-id credential-ids]
        (try
          (let [[resource-name uuid] (u/parse-id credential-id)
                request {:params      {:resource-name credential/resource-type
                                       :uuid          uuid}
                         :nuvla/authn authn-info}]
            (crud/delete request))
          (catch Exception e
            (log/error (str "error deleting " (:id credential-id) " for " deployment-id ": " e))))))
    (catch Exception e
      (log/error "cannot query credentials related to " deployment-id))))


(defn delete-deployment-parameters
  "Attempts to delete all deployment-parameter resources associated with the
   deployment via the parent attribute. Exceptions are logged but otherwise
   ignored."
  [authn-info deployment-id]
  (try
    (let [query  {:params      {:resource-name "deployment-parameter"} ;; hard-coded to avoid cyclic dependency
                  :cimi-params {:filter (cimi-params-impl/cimi-filter {:filter (str "parent='" deployment-id "'")})
                                :select ["id"]}
                  :nuvla/authn authn-info}
          dp-ids (->> query crud/query :body :resources (map :id))]

      (doseq [dp-id dp-ids]
        (try
          (let [[resource-name uuid] (u/parse-id dp-id)
                request {:params      {:resource-name resource-name
                                       :uuid          uuid}
                         :nuvla/authn authn-info}]
            (crud/delete request))
          (catch Exception e
            (log/error (str "error deleting " (:id dp-id) " for " deployment-id ": " e))))))
    (catch Exception e
      (log/error "cannot query deployment-parameter resources related to " deployment-id))))


(defn resolve-module [{:keys [href]} authn-info]
  (if-let [params (u/id->request-params href)]
    (let [request-module {:params params, :nuvla/authn authn-info}
          {:keys [body status] :as module-response} (crud/retrieve request-module)]
      (if (= status 200)
        (let [module-resolved (-> body
                                  (dissoc :versions :operations)
                                  (std-crud/resolve-hrefs authn-info true))]
          (assoc module-resolved :href href))
        (throw (ex-info (str "cannot resolve module " href) body))))
    (throw (r/ex-bad-request "deployment module is not defined"))))


(defn can-delete?
  [{:keys [state] :as resource}]
  (#{"CREATED" "STOPPED" "ERROR"} state))


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (can-delete? resource)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 409 id))))


(defn remove-delete
  [operations]
  (vec (remove #(= (name :delete) (:rel %)) operations)))
