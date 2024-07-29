(ns com.sixsq.nuvla.server.resources.callback-deployment-update
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment :as depl]
    [com.sixsq.nuvla.server.resources.notification :as notif]))


(def ^:const action-name "deployment-update")


(defn edit-deployment
  [module-content request]
  (let [deployment-current (:body (crud/retrieve request))
        deployment-new     (update-in deployment-current
                                      [:module :content] #(merge % module-content))
        new-request        (assoc request :request-method :put :body deployment-new)]
    (crud/edit new-request)))


(defn update-deployment!
  [module-content request]
  (edit-deployment module-content request)
  (depl/update-deployment-impl request))


(defn delete-notification!
  [callback-id]
  (let [filter   (format "callback='%s'" callback-id)
        options  {:cimi-params {:filter (parser/parse-cimi-filter filter)}}
        notif-id (-> (crud/query-as-admin notif/resource-type options)
                     second
                     first
                     :id)]
    (when notif-id
      (crud/delete {:params      {:uuid          (some-> notif-id (str/split #"/") second)
                                  :resource-name notif/resource-type}
                    :nuvla/authn auth/internal-identity}))))


(defmethod callback/execute action-name
  [{{dpl-id :href} :target-resource callback-id :id :as callback-resource} request]
  (let [depl-req-base   {:params         {:uuid          (u/id->uuid dpl-id)
                                          :resource-name depl/resource-type}
                         :request-method :get
                         :nuvla/authn    (:nuvla/authn request)}
        module-content  (:data callback-resource)
        update-response (update-deployment! module-content depl-req-base)]
    (when (= (:status update-response) 202)
      (delete-notification! callback-id))
    update-response))
