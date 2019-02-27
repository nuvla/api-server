(ns sixsq.nuvla.server.resources.deployment.utils
  (:require [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
            [sixsq.nuvla.server.resources.credential :as credential]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.module :as module]
            [clojure.string :as str]
            [sixsq.nuvla.util.response :as r]
            [sixsq.nuvla.server.resources.common.schema :as c]))


(defn generate-api-key-secret
  [{:keys [identity] :as request}]
  (let [
        request-api-key {:params   {:resource-name credential/resource-type}
                         :body     {:template {:href (str "credential-template/" cred-api-key/method)}}
                         :identity identity}
        {{:keys [status resource-id secretKey] :as body} :body :as response} (crud/add request-api-key)]
    (if (= status 201)
      {:api-key    resource-id
       :api-secret secretKey}
      (throw (ex-info "" body)))))


(defn resolve-module [{:keys [href]} idmap]
  (let [request-module {:params   {:uuid          (some-> href (str/split #"/") second)
                                   :resource-name module/resource-type}
                        :identity idmap}
        {:keys [body status] :as module-response} (crud/retrieve request-module)]
    (if (= status 200)
      (let [module-resolved (-> body
                                (dissoc :versions :operations)
                                (std-crud/resolve-hrefs idmap true))]
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
