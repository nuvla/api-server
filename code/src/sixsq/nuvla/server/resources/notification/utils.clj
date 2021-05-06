(ns sixsq.nuvla.server.resources.notification.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.subscription :as subs]
    [sixsq.nuvla.server.resources.subscription-config :as subs-conf]))


(defn create-subscription
  [resource-id type kind category method authn-info]
  (try
    (let [subscription {:type     type
                        :kind     kind
                        :category category
                        :resource resource-id
                        :status   "enabled"
                        :method   method
                        :acl      (a/default-acl authn-info)}
          create-request {:params      {:resource-name subs/resource-type}
                          :body        subscription
                          :nuvla/authn auth/internal-identity}]
      (crud/add create-request))
     (catch Exception e
       (log/errorf "Failed to create subscription: %s" e))))


(defn create-subscription-if-enabled
  [resource-id type kind category request]
  (let [filter (format "type='%s' and category='%s' and collection='infrastructure-service' and enabled=true"
                       type category)
        authn-info (auth/current-authentication request)
        query {:params      {:resource-name subs-conf/resource-type}
               :cimi-params {:filter (parser/parse-cimi-filter filter)
                             :last   1}
               :nuvla/authn authn-info}
        notif-method (-> query
                         crud/query
                         :body
                         :resources
                         first
                         :method)]
    (when-not (str/blank? notif-method)
      (create-subscription resource-id type kind category notif-method authn-info))))


(defn create-state-event-notification-subscription
  [resource-id request]
  (create-subscription-if-enabled resource-id "notification" "event" "state" request))
