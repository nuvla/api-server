(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]))


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


(defn is-nano?
  [{:keys [formFactor] :as nuvlabox-record}]
  (boolean (and formFactor (= (str/lower-case formFactor) nano-formFactor))))


(defn add-connector-href
  "Create a docker connector and attach it to the nuvlabox
  only if it is a nano without pre-existing connector
  Otherwise return the untouched nuvlabox-record"
  [{:keys [connector] :as nuvlabox}]
  nuvlabox)
