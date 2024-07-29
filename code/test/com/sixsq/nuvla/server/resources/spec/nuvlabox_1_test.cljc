(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-1-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-1 :as nb-1]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def valid-nb {:id                             (str nb/resource-type "/uuid")
               :resource-type                  nb/resource-type
               :created                        timestamp
               :updated                        timestamp
               :acl                            valid-acl

               :version                        1

               :nuvlabox-status                "nuvlabox-status/abcdef"
               :infrastructure-service-group   "infrastructure-service-group/abcdef"

               :state                          "NEW"
               :owner                          "user/test"
               :refresh-interval               90

               :location                       [46.2044 6.1432 373.]
               :inferred-location              [46.2044 6.1432 373.]

               :supplier                       "super-hardware-company"
               :organization                   "MyNanoCompany"

               :manufacturer-serial-number     "1234"
               :firmware-version               "10"
               :hardware-type                  "arm"
               :form-factor                    "nano"

               :wifi-ssid                      "ssid"
               :wifi-password                  "secure-string"
               :root-password                  "more-secure-string"

               :login-username                 "l-user"
               :login-password                 "l-password"
               :cloud-password                 "c-password"

               :comment                        "nuvlabox shutdown because it was tired"

               :vm-cidr                        "0.0.0.0/32"
               :lan-cidr                       "0.0.0.0/32"
               :os-version                     "OS version"
               :hw-revision-code               "a020d3"
               :monitored                      true
               :vpn-server-id                  "infrastructure-service/uuid-1"
               :internal-data-gateway-endpoint "nb-data-gateway"
               :ssh-keys                       ["credential/aaa-bbb-ccc", "credential/111-222-ccc"]
               :capabilities                   ["NUVLA_JOB_PULL", "SYS_ADMIN"]
               :online                         true
               :host-level-management-api-key  "credential/123-abc"
               :heartbeat-interval             20})


(deftest check-nuvlabox

  (stu/is-valid ::nb-1/schema valid-nb)
  (s/explain ::nb-1/schema valid-nb)
  (stu/is-invalid ::nb-1/schema (assoc valid-nb :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :state :owner :refresh-interval}]
    (stu/is-invalid ::nb-1/schema (dissoc valid-nb attr)))

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
                 :infrastructure-service-id
                 :ssh-keys
                 :capabilities
                 :online
                 :host-level-management-api-key
                 :heartbeat-interval}]
    (stu/is-valid ::nb-1/schema (dissoc valid-nb attr))))
