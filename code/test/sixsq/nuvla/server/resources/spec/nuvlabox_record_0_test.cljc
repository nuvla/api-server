(ns sixsq.nuvla.server.resources.spec.nuvlabox-record-0-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.spec.nuvlabox-record-0 :as nb-record-0]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def valid-nb-record {:id                         (str nb/resource-type "/uuid")
                      :resource-type              nb/resource-type
                      :created                    timestamp
                      :updated                    timestamp
                      :acl                        valid-acl

                      :version                    0

                      :state                      "NEW"
                      :mac-address                "aa:bb:cc:dd:ee:ff"
                      :owner                      {:href "user/test"}
                      :refresh-interval           90

                      :info                       {:href "nuvlabox-state/state-uuid"}

                      :location                   [46.2044 6.1432 373.]

                      :supplier                   "super-hardware-company"
                      :organization               "MyNanoCompany"

                      :manufacturer-serial-number "1234"
                      :firmware-version           "10"
                      :hardware-type              "arm"
                      :form-factor                "nano"

                      :wifi-ssid                  "ssid"
                      :wifi-password              "secure-string"
                      :root-password              "more-secure-string"

                      :login-username             "l-user"
                      :login-password             "l-password"
                      :cloud-password             "c-password"

                      :comment                    "nuvlabox shutdown because it was tired"

                      :vm-cidr                    "0.0.0.0/32"
                      :lan-cidr                   "0.0.0.0/32"
                      :os-version                 "OS version"
                      :hw-revision-code           "a020d3"})


(deftest check-nuvlabox-state

  (stu/is-valid ::nb-record-0/schema valid-nb-record)
  (stu/is-invalid ::nb-record-0/schema (assoc valid-nb-record :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :state :mac-address :owner :refresh-interval}]
    (stu/is-invalid ::nb-record-0/schema (dissoc valid-nb-record attr)))

  ;; optional
  (doseq [attr #{:info
                 :location
                 :supplier
                 :organization
                 :manufacturer-serial-number
                 :firmware-version
                 :hardware-type
                 :form-factor
                 :wifi-ssid
                 :wifi-password
                 :root-password
                 :login-username
                 :login-password
                 :cloud-password
                 :comment
                 :vm-cidr
                 :lan-cidr
                 :os-version
                 :hw-revision-code}]
    (stu/is-valid ::nb-record-0/schema (dissoc valid-nb-record attr))))
