(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(defn generate-api-key-secret
  [authn-info]
  (let [request-api-key {:params      {:resource-name credential/resource-type}
                         :body        {:template {:href (str "credential-template/" cred-api-key/method)}}
                         :nuvla/authn authn-info}
        {{:keys [status resource-id secretKey] :as body} :body :as response} (crud/add request-api-key)]
    (if (= status 201)
      {:api-key    resource-id
       :api-secret secretKey}
      (throw (ex-info "" body)))))


(defn delete-deployment-credentials
  "Attempts to delete all credentials associated with the deployment via the
   parent attribute. Exceptions are logged but otherwise ignored."
  [authn-info deployment-id]
  (try
    (let [credentials-query {:params      {:resource-name credential/resource-type}
                             :cimi-params {:filter (cimi-params-impl/cimi-filter {:filter (str "parent='" deployment-id "'")})
                                           :select ["id"]}
                             :nuvla/authn authn-info}
          credential-ids (->> credentials-query crud/query :body :resources (map :id))]

      (doseq [credential-id credential-ids]
        (try
          (let [[resource-name uuid] (u/split-resource-id credential-id)
                request {:params      {:resource-name credential/resource-type
                                       :uuid          uuid}
                         :nuvla/authn authn-info}]
            (crud/delete request))
          (catch Exception e
            (log/error (str "error deleting " (:id credential-id) " for " deployment-id ": " e))))))
    (catch Exception e
      (log/error "cannot query credentials related to " deployment-id))))


(defn resolve-module [{:keys [href]} authn-info]
  (let [request-module {:params      {:uuid          (some-> href (str/split #"/") second)
                                      :resource-name module/resource-type}
                        :nuvla/authn authn-info}
        {:keys [body status] :as module-response} (crud/retrieve request-module)]
    (if (= status 200)
      (let [module-resolved (-> body
                                (dissoc :versions :operations)
                                (std-crud/resolve-hrefs authn-info true))]
        (assoc module-resolved :href href))
      (throw (ex-info nil body)))))


(defn can-delete?
  [{:keys [state] :as resource}]
  (#{"CREATED" "STOPPED"} state))


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (can-delete? resource)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 409 id))))


(defn remove-delete
  [operations]
  (vec (remove #(= (:delete c/action-uri) (:rel %)) operations)))
