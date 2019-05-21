(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-tmpl-api]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.response :as r]))


(defn create-infrastructure-service-group
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox."
  [{:keys [id acl] :as nuvlabox}]
  (let [skeleton {:name        (str "service group for " id)
                  :description (str "services available on the NuvlaBox " id)
                  :parent      id
                  :acl         acl}
        {:keys [status body] :as resp} (service-group/create-infrastructure-service-group skeleton)]
    (if (= 201 status)
      (assoc nuvlabox :infrastructure-service-group (:resource-id body))
      (let [msg (str "creating infrastructure-service-group resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn create-nuvlabox-status
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox."
  [{:keys [id version acl] :as nuvlabox}]
  (let [{:keys [status body] :as resp} (nb-status/create-nuvlabox-status version id acl)]
    (if (= 201 status)
      (assoc nuvlabox :nuvlabox-status (:resource-id body))
      (let [msg (str "creating nuvlabox-status resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn create-nuvlabox-api-key
  "Create api key that allow NuvlaBox to update it's own state."
  [{:keys [id name acl] :as nuvlabox}]
  (let [identity {:user-id id
                  :claims  #{id "group/nuvla-user" "group/nuvla-anon"}}

        cred-tmpl {:name        (str "Generated API Key for " (or name id))
                   :description (str/join " " ["Generated API Key for" name (str "(" id ")")])
                   :template    {:href   (str "credential-template/" cred-tmpl-api/method)
                                 :type   cred-tmpl-api/credential-type
                                 :method cred-tmpl-api/method
                                 :parent id
                                 :ttl    0
                                 :acl    acl}}

        {:keys [status body] :as resp} (credential/create-credential cred-tmpl identity)
        {:keys [resource-id secret-key]} body]
    (if (= 201 status)
      {:api-key    resource-id
       :secret-key secret-key}
      (let [msg (str "creating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn get-isg-id
  "Finds the infrastructure-service-group that is associated with the given
   nuvlabox-id."
  [nuvlabox-id]
  (let [filter (format "parent='%s'" nuvlabox-id)
        body {:cimi-params {:filter (parser/parse-cimi-filter filter)
                            :select ["id"]}
              :nuvla/authn auth/internal-identity}]
    (-> (db/query isg/resource-type body)
        second
        first
        :id)))


(defn create-minio-service
  "Creates an infrastructure-service that describes a Minio endpoint
   associated with a nuvlabox."
  [nuvlabox-id isg-id endpoint]
  (let [isg-id (get-isg-id nuvlabox-id)
        request {:params      {:resource-name infra-service/resource-type}
                 :body        {:name        "Minio (S3)"
                               :description (str "Minio (S3) for " nuvlabox-id)
                               :template    {:href     "infrastructure-service-template/generic"
                                             :parent   isg-id
                                             :endpoint endpoint
                                             :acl      {:owners   ["group/nuvla-admin"]
                                                        :edit-acl [nuvlabox-id]}}}
                 :nuvla/authn auth/internal-identity}
        {{:keys [resource-id]} :body status :status} (crud/add request)]

    (if (= 201 status)
      (log/info "minio service" resource-id "created")
      (let [msg (str "cannot create minio service for " nuvlabox-id)]
        (throw (ex-info msg (r/map-response msg 400 "")))))))
