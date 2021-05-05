(ns sixsq.nuvla.server.resources.credential.vpn-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-customer
     :as tpl-customer]
    [sixsq.nuvla.server.util.log :as logu]))


(defn get-service
  [authn-info is-id]
  (let [opts {:nuvla/authn authn-info}]
    (crud/retrieve-by-id is-id opts)))


(defn get-configuration
  [is-id]
  (let [filter  (format "infrastructure-services='%s'" is-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)}}]
    (-> (crud/query-as-admin configuration/resource-type options)
        second
        first)))


(defn credentials-already-exist?
  [is-id user-id]
  (let [filter  (format "parent='%s' and vpn-certificate-owner='%s' and subtype='%s'"
                        is-id user-id tpl-customer/credential-subtype)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)}}]
    (-> (crud/query-as-admin credential/resource-type options)
        first
        :count
        pos?)))


(defn generate-credential
  [vpn-endpoint user-id vpn_service_id csr]
  (-> vpn-endpoint
      (http/post
        {:form-params        (cond-> {:requester_id   user-id
                                      :vpn_service_id vpn_service_id}
                                     csr (assoc :csr csr))
         :content-type       :json
         :socket-timeout     30000
         :connection-timeout 30000})
      :body
      (json/read-str :key-fn keyword)))


(defn try-generate-credential
  [vpn-endpoint user-id vpn_service_id csr]
  (try
    (generate-credential vpn-endpoint user-id vpn_service_id csr)
    (catch Exception e
      (log/error (str e))
      (logu/log-and-throw 500 "Exception during generation of credential on VPN!"))))


(defn delete-credential
  [vpn-endpoint cred-id]
  (http/delete vpn-endpoint
               {:form-params        {:credential_id cred-id}
                :content-type       :json
                :socket-timeout     30000
                :connection-timeout 30000}))


(defn try-delete-credential
  [vpn-endpoint cred-id]
  (try
    (delete-credential vpn-endpoint cred-id)
    (catch Exception e
      (log/error (str e))
      (logu/log-and-throw 500 "Exception during deletion of credential on VPN!"))))


(defn check-service-subtype
  [service]
  (when (not= (:subtype service) "vpn")
    (logu/log-and-throw-400
      "Bad infrastructure service subtype. Subtype should be vpn!")))


(defn check-scope
  [service expected-scope]
  (when (not= (:vpn-scope service) expected-scope)
    (logu/log-and-throw-400
      "Bad infrastructure service scope for selected credential template!")))


(defn check-existing-credential
  [is-id user-id]
  (when (credentials-already-exist? is-id user-id)
    (logu/log-and-throw-400
      "Credential VPN already exist for your account on selected VPN infrastructure service!")))


(defn check-vpn-endpoint
  [is-id endpoint]
  (when-not endpoint
    (logu/log-and-throw-400
      (format "No vpn api endpoint found for '%s'." is-id))))
