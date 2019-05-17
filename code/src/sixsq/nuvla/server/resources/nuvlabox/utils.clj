(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-tmpl-api]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [sixsq.nuvla.server.resources.nuvlabox-state :as nb-state]
    [sixsq.nuvla.server.util.response :as r]))


(defn create-infrastructure-service-group
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox-record."
  [{:keys [id acl] :as nuvlabox-record}]
  (let [skeleton {:name        (str "service group for " id)
                  :description (str "services available on the NuvlaBox " id)
                  :parent      id
                  :acl         acl}
        {:keys [status body] :as resp} (service-group/create-infrastructure-service-group skeleton)]
    (if (= 201 status)
      (assoc nuvlabox-record :infrastructure-service-group (:resource-id body))
      (let [msg (str "creating infrastructure-service-group resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn create-nuvlabox-state
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox-record."
  [{:keys [id version acl] :as nuvlabox-record}]
  (let [{:keys [status body] :as resp} (nb-state/create-nuvlabox-state version id acl)]
    (if (= 201 status)
      (assoc nuvlabox-record :nuvlabox-state (:resource-id body))
      (let [msg (str "creating nuvlabox-state resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn create-nuvlabox-api-key
  "Create api key that allow NuvlaBox to update it's own state."
  [{:keys [id name acl] :as nuvlabox-record}]
  (let [identity  {:user-id id
                   :claims  #{"group/nuvla-user" "group/nuvla-anon"}}

        cred-acl  (-> acl
                      (update :edit-acl (comp vec set concat) (:owners acl))
                      (assoc :owners ["group/nuvla-admin"]))

        cred-tmpl {:name        (str "Generated API Key for " (or name id))
                   :description (str/join " " ["Generated API Key for" name (str "(" id ")")])
                   :template    {:href   (str "credential-template/" cred-tmpl-api/method)
                                 :type   cred-tmpl-api/credential-type
                                 :method cred-tmpl-api/method
                                 :parent id
                                 :ttl    100
                                 :acl    cred-acl}}

        {:keys [status body] :as resp} (credential/create-credential cred-tmpl identity)
        {:keys [resource-id secret-key]} body]
    (if (= 201 status)
      {:api-key    resource-id
       :secret-key secret-key}
      (let [msg (str "creating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))
