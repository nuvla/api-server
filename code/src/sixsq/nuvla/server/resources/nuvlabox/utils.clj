(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.nuvlabox-identifier :as idf]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.auth.utils :as auth]))


;;TODO avoid hardcoding vpn configuration
(def ^:const vpn-endpoint-ip "172.16.1.1")
(def ^:const vpn-endpoint-port 5000)
(def ^:const nano-formFactor "nano")
(def ^:const default-serie "exoplanets")


(defn get-url
  ([ip port ssl?]
   (let [protocol (if ssl? "https" "http")]
     (when (and ip port)
       (str protocol "://" ip ":" port))))
  ([ip port]
   (get-url ip port false)))


(defn generate-docker-connector
  [{:keys [identifier vpnIP] :as nuvlabox}]
  nil)



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


(defn list-all-identifiers-for-series
  [series]
  (let [filter-str (format "series='%s' " series)
        filter {:filter (parser/parse-cimi-filter filter-str)}]
    (->> (try
           (db/query idf/resource-type {:cimi-params filter
                                        :nuvla/authn auth/internal-identity})
           (catch Exception _ []))
         second
         (map :identifier))))


(defn already-used-identifiers
  []
  (->> (db/query "nuvlabox-record" {:nuvla/authn auth/internal-identity})
       second
       (map :identifier)))


(defn random-choose-remaining-identifier
  [series]
  (let [remaining-identifiers (vec (clojure.set/difference
                                     (set (list-all-identifiers-for-series series))
                                     (set (already-used-identifiers))))]
    (when (> (count remaining-identifiers) 0)
      (-> remaining-identifiers
          (rand-nth)))))

(defn isNano?
  [{:keys [formFactor] :as nuvlabox-record}]
  (boolean (and formFactor (= (str/lower-case formFactor) nano-formFactor))))

(defn add-identifier
  [nuvlabox]
  (if (isNano? nuvlabox)
    (let [random-identifier (random-choose-remaining-identifier default-serie)]
      (assoc nuvlabox :identifier random-identifier :name random-identifier))
    nuvlabox))


(defn find-nuvlabox-quotas
  [organization request]
  nil)


(defn quota-ok? [{:keys [organization] :as nuvlabox} request]
  true)

(defn add-connector-href
  "Create a docker connector and attach it to the nuvlabox
  only if it is a nano without pre-existing connector
  Otherwise return the untouched nuvlabox-record"
  [{:keys [connector] :as nuvlabox}]
  nuvlabox)


(defn nuvlabox-online-email-body
  [{identifier :identifier id :id}]
  (format
    (str/join "\n"
              ["The NuvlaBox %s (%s) is back online."
               ""])
    identifier
    id))

(defn nuvlabox-offline-email-body
  [{identifier :identifier id :id delay :notificationDelay}]
  (format
    (str/join "\n"
              ["The NuvlaBox %s (%s) last connected more than %d minutes ago."
               ""
               "You will be notified when it comes back online."])
    identifier
    id
    (or delay 60)))

(defn send-nuvlabox-online-email
  [{identifier :identifier addresses :notificationEmails :as nuvlabox-record}]
  nil)

(defn send-nuvlabox-offline-email
  [{identifier :identifier addresses :notificationEmails :as nuvlabox-record}]
  nil)

(defn create-nuvlabox-online-notification
  [{id :id identifier :identifier acl :acl}]
  nil)

(defn create-nuvlabox-offline-notification
  [{id :id identifier :identifier acl :acl}]
  nil)

(defn handle-nuvlabox-state-state-change
  [current new]
  (let [old-state (:state current)
        new-state (:state new)]
    (when (not= new-state old-state)
      (let [nuvlabox-record-id (-> current :nuvlabox :href)
            nuvlabox-record (db/retrieve nuvlabox-record-id {})]
        (cond
          (= new-state "online") (do (create-nuvlabox-online-notification nuvlabox-record)
                                     (send-nuvlabox-online-email nuvlabox-record))
          (= new-state "offline") (do (create-nuvlabox-offline-notification nuvlabox-record)
                                      (send-nuvlabox-offline-email nuvlabox-record)))))))
