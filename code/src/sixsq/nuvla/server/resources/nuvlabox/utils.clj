(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
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
