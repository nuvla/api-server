(ns sixsq.nuvla.server.resources.callback-module-update
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.module :as m]))


(def ^:const action-name "module-update")


(defn update-component!
  [module-id data]
  (let [module     (:body (crud/retrieve {:params         {:uuid          (u/id->uuid module-id)
                                                           :resource-name m/resource-type}
                                          :request-method :get
                                          :nuvla/authn    auth/internal-identity}))
        new-module (update module :content #(merge % data))]
    (crud/edit {:params         {:uuid          (u/id->uuid module-id)
                                 :resource-name m/resource-type}
                :request-method :put
                :body           new-module
                :nuvla/authn    auth/internal-identity})))


(defmethod callback/execute action-name
  [{{module-id :href} :target-resource :as callback-resource} request]
  (update-component! module-id (:data callback-resource)))
