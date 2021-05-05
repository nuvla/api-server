(ns sixsq.nuvla.server.resources.callback-module-update
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.module :as m]
    [sixsq.nuvla.server.resources.notification :as notif]))


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
  [{{module-id :href} :target-resource callback-id :id :as callback-resource} _request]
  (let [update-response (update-component! module-id (:data callback-resource))]
    (when (= (:status update-response) 200)
      (delete-notification! callback-id))
    update-response))
