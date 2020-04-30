(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.deployment-log :as deployment-log]
    [sixsq.nuvla.server.util.log :as logu]
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


(defn delete-child-resources
  "Attempts to delete (as admin) all child resources associated with the
   deployment via the parent attribute. The type of resource is provided as a
   parameter. Exceptions are logged but otherwise ignored."
  [resource-name deployment-id]
  (try
    (let [query     {:params      {:resource-name resource-name}
                     :cimi-params {:filter (cimi-params-impl/cimi-filter {:filter (str "parent='" deployment-id "'")})
                                   :select ["id"]}
                     :nuvla/authn auth/internal-identity}
          child-ids (->> query crud/query :body :resources (map :id))]

      (doseq [child-id child-ids]
        (try
          (let [[resource-name uuid] (u/parse-id child-id)
                request {:params      {:resource-name resource-name
                                       :uuid          uuid}
                         :nuvla/authn auth/internal-identity}]
            (crud/delete request))
          (catch Exception e
            (log/error (str "error deleting " (:id child-id) " for " deployment-id ": " e))))))
    (catch Exception _
      (log/errorf "cannot query %s resources related to %s" resource-name deployment-id))))


(defn delete-all-child-resources
  "Attempts to delete (as admin) all credential, deployment-parameter, and
   deployment-log resources associated with the deployment. Exceptions are
   logged but otherwise ignored."
  [deployment-id]
  (doseq [resource-name #{credential/resource-type "deployment-parameter" deployment-log/resource-type}]
    (delete-child-resources resource-name deployment-id)))


(defn resolve-module [href authn-info]
  (let [params  (u/id->request-params href)
        request {:params params, :nuvla/authn authn-info}
        {:keys [body status] :as response} (crud/retrieve request)]
    (if (= status 200)
      {:module (-> body
                   (dissoc :versions :operations)
                   (std-crud/resolve-hrefs authn-info true)
                   (assoc :href href))}
      (throw (r/ex-bad-request (str "cannot resolve " href))))))

(defn resolve-deployment [href authn-info]
  (let [params  (u/id->request-params href)
        request {:params params, :nuvla/authn authn-info}
        {:keys [body status] :as response} (crud/retrieve request)]
    (if (= status 200)
      (select-keys body [:module :data :name :description :tags])
      (throw (r/ex-bad-request (str "cannot resolve " href))))))


(defn create-deployment
  [{:keys [body] :as request}]
  (let [authn-info (auth/current-authentication request)
        href       (or (get-in body [:module :href])
                       (get-in body [:deployment :href]))
        error-msg  "Request body is missing a module or a deployment href map to create from!"]
    (cond
      (str/starts-with? href "module/") (resolve-module href authn-info)
      (str/starts-with? href "deployment/") (resolve-deployment href authn-info)
      :else (logu/log-and-throw-400 error-msg))))


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
