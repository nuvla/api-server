(ns sixsq.nuvla.server.resources.spec.nuvlabox-record-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.spec.nuvlabox-record :as nuvlabox-record]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox {:id              (str nb/resource-type "/uuid")
               :resource-type   nb/resource-type
               :created         timestamp
               :updated         timestamp
               :acl             valid-acl

               :identifier      "Albert Einstein"
               :connector       {:href "connector/nuvlabox-albert-einstein"}
               :formFactor      "Nuvlabox"
               :state           "new"
               :macAddress      "aa:bb:cc:dd:ee:ff"
               :owner           {:href "user/test"}
               :sslCA           "ssl ca"
               :sslCert         "ssl cert"
               :sslKey          "ssl key"
               :vmCidr          "10.0.0.0/24"
               :lanCidr         "10.0.1.0/24"
               :vpnIP           "10.0.0.2"
               :refreshInterval 60})

(def nano {:id              (str nb/resource-type "/uuid")
           :resource-type   nb/resource-type
           :created         timestamp
           :updated         timestamp
           :acl             valid-acl
           :formFactor      "nano"
           :identifier      "Andromeda-42"
           :state           "new"
           :macAddress      "aa:bb:cc:dd:ee:ff"
           :owner           {:href "user/test"}
           :OSVersion       "OS version"
           :hwRevisionCode  "a020d3"
           :rootPassword    "password"
           :loginUsername   "l-user"
           :loginPassword   "l-password"
           :organization    "MyNanoCompany"
           :CPU             4
           :RAM             976
           :refreshInterval 90})


(deftest check-nuvlabox-state

  (doseq [record [nuvlabox nano]]

    (stu/is-valid ::nuvlabox-record/nuvlabox-record record)
    (stu/is-invalid ::nuvlabox-record/nuvlabox-record (assoc record :bad-attr "BAD_ATTR"))

    ;; required
    (doseq [attr #{:id :resource-type :created :updated :acl
                   :identifier
                   :state
                   :macAddress
                   :owner
                   :refreshInterval}]
      (stu/is-invalid ::nuvlabox-record/nuvlabox-record (dissoc record attr)))

    ;; optional
    (doseq [attr #{:connector
                   :info
                   :user
                   :location
                   :supplier
                   :organization
                   :manufacturerSerialNumber
                   :firmwareVersion
                   :hardwareType
                   :formFactor
                   :wifiSSID
                   :wifiPassword
                   :rootPassword
                   :loginUsername
                   :loginPassword
                   :cloudPassword
                   :comment
                   :sslCA
                   :sslCert
                   :sslKey
                   :vmCidr
                   :lanCidr
                   :vpnIP
                   :vpnServerIP
                   :vpnServerPort
                   :OSVersion
                   :hwRevisionCode
                   :CPU
                   :RAM}]
      (stu/is-valid ::nuvlabox-record/nuvlabox-record (dissoc record attr)))))
