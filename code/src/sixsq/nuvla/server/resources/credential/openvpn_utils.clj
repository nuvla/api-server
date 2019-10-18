(ns sixsq.nuvla.server.resources.credential.openvpn-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-customer
     :as tpl-customer]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]))


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
  (let [filter  (format "parent='%s' and openvpn-common-name='%s' and subtype='%s'"
                        is-id user-id tpl-customer/credential-subtype)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)}}]
    (-> (crud/query-as-admin credential/resource-type options)
        first
        :count
        pos?)))


(defn generate-credential
  ([openvpn-endpoint user-id vpn_service_id]
   (generate-credential openvpn-endpoint user-id vpn_service_id nil))
  ([openvpn-endpoint user-id vpn_service_id csr]
   (-> (http/post openvpn-endpoint
                  {:form-params        (cond-> {:requester_id   user-id
                                                :vpn_service_id vpn_service_id}
                                               csr (assoc :csr csr))
                   :content-type       :json
                   :socket-timeout     30000
                   :connection-timeout 30000})
       :body (json/read-str :key-fn keyword))))
