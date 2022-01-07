(ns sixsq.nuvla.server.resources.spec.nuvlabox-0-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.spec.nuvlabox-0 :as nb-0]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def valid-nb {:id                            (str nb/resource-type "/uuid")
               :resource-type                 nb/resource-type
               :created                       timestamp
               :updated                       timestamp
               :acl                           valid-acl

               :version                       0

               :nuvlabox-status               "nuvlabox-status/abcdef"
               :infrastructure-service-group  "infrastructure-service-group/abcdef"

               :state                         "NEW"
               :owner                         "user/test"
               :refresh-interval              90

               :location                      [46.2044 6.1432 373.]
               :inferred-location             [46.2044 6.1432 373.]

               :supplier                      "super-hardware-company"
               :organization                  "MyNanoCompany"

               :manufacturer-serial-number    "1234"
               :firmware-version              "10"
               :hardware-type                 "arm"
               :form-factor                   "nano"

               :wifi-ssid                     "ssid"
               :wifi-password                 "secure-string"
               :root-password                 "more-secure-string"

               :login-username                "l-user"
               :login-password                "l-password"
               :cloud-password                "c-password"

               :comment                       "nuvlabox shutdown because it was tired"

               :vm-cidr                       "0.0.0.0/32"
               :lan-cidr                      "0.0.0.0/32"
               :os-version                    "OS version"
               :hw-revision-code              "a020d3"
               :monitored                     true
               :ssh-keys                      []
               :capabilities                  []
               :online                        true
               :host-level-management-api-key "credential/123-abc"})


(deftest check-nuvlabox

  (stu/is-valid ::nb-0/schema valid-nb)
  (stu/is-invalid ::nb-0/schema (assoc valid-nb :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :state :owner :refresh-interval}]
    (stu/is-invalid ::nb-0/schema (dissoc valid-nb attr)))

  ;; optional
  (doseq [attr #{:nuvlabox-status
                 :infrastructure-service-group
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
                 :hw-revision-code
                 :monitored
                 :ssh-keys
                 :capabilities
                 :online
                 :host-level-management-api-key}]
    (stu/is-valid ::nb-0/schema (dissoc valid-nb attr))))
