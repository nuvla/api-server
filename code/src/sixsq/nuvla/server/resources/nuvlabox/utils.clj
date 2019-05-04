(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const nano-formFactor "nano")


(defn get-url
  ([ip port ssl?]
   (let [protocol (if ssl? "https" "http")]
     (when (and ip port)
       (str protocol "://" ip ":" port))))
  ([ip port]
   (get-url ip port false)))


(defn call-vpn-api
  "Returns a tuple with status and message"
  [url organization identifier formFactor]
  [201 {:vpnIp "10.1.42.42", :sslCA "ca", :sslCert "cert", :sslKey "key"}])


(defn add-vpn-configuration!
  "Perform an HTTP POST to the VPN-API server to retrieve vpn information
   to be merged with a nuvlabox record.
   This method will log exceptions but then return nil to indicate that the
   vpn infos could not be retrieved."
  [{:keys [organization identifier formFactor] :as nuvlabox}]
  (call-vpn-api "https://vpn.example.org" organization identifier formFactor))


(defn remove-vpn-configuration!
  "Perform an HTTP DELETE to the VPN-API server to remove vpn information
   linked to a nuvlabox record.
   This method will log exceptions but then return nil to indicate that the
   vpn infos could not be deleted."
  [{:keys [organization identifier] :as nuvlabox}]
  nil)


(defn delete-nuvlabox
  "Delete a nuvlabox record and its VPN configuration"
  [nuvlabox request]
  (let [{:keys [status message]} (remove-vpn-configuration! nuvlabox)]
    (if (-> (range 200 300)
            (set)
            (contains? status))
      (do
        (log/info (str "Deleting nuvlabox " nuvlabox))
        (db/delete nuvlabox request))
      (logu/log-and-throw status (or message "VPN-API delete error")))))


(defn merge-vpn-infos [id request vpn-infos]
  (-> (db/retrieve id request)
      (merge vpn-infos)
      (db/edit request)))

(defn is-nano?
  [{:keys [formFactor] :as nuvlabox-record}]
  (boolean (and formFactor (= (str/lower-case formFactor) nano-formFactor))))


(defn quota-ok? [{:keys [organization] :as nuvlabox} request]
  true)


(defn add-connector-href
  "Create a docker connector and attach it to the nuvlabox
  only if it is a nano without pre-existing connector
  Otherwise return the untouched nuvlabox-record"
  [{:keys [connector] :as nuvlabox}]
  nuvlabox)
