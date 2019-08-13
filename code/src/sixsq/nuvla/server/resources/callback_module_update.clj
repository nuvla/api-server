(ns sixsq.nuvla.server.resources.callback-module-update
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.notification :refer [resource-type]]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.module :as m]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "module-update")


(defn update-component!
  [module-id data]
  (let [module (crud/retrieve-by-id module-id)
        new-content (-> module
                        :versions
                        last
                        :href
                        crud/retrieve-by-id
                        (merge data)
                        u/strip-service-attrs)
        new-module (merge module {:content new-content})]
    (crud/edit {:params         {:uuid          (u/id->uuid module-id)
                                 :resource-name m/resource-type}
                :request-method :put
                :body           new-module
                :nuvla/authn    auth/internal-identity})))


(defmethod callback/execute action-name
  [{{id :href} :target-resource :as callback-resource} request]
  (let [msg (str id " successfully updated")]
    (update-component! id (:data callback-resource))
    (log/info msg)
    (r/map-response msg 200 id)))
