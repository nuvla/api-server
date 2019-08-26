(ns sixsq.nuvla.server.resources.callback-deployment-update
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment :as depl]
    [sixsq.nuvla.server.resources.notification :refer [resource-type]]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "deployment-update")


(defn edit-deployment
  [module-content request]
  (let [deployment-current (:body (crud/retrieve request))
        deployment-new     (update-in deployment-current [:module :content] #(merge % module-content))
        new-request        (assoc request :request-method :put :body deployment-new)]
    (crud/edit new-request)))


(defn update-deployment!
  [module-content request]
  (edit-deployment module-content request)
  (depl/update-deployment-impl request))


(defmethod callback/execute action-name
  [{{dpl-id :href} :target-resource :as callback-resource} request]
  (let [depl-req-base  {:params         {:uuid          (u/id->uuid dpl-id)
                                         :resource-name depl/resource-type}
                        :request-method :get
                        :nuvla/authn    (:nuvla/authn request)}
        module-content (:data callback-resource)]
    (update-deployment! module-content depl-req-base)))
