(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-0-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nbs]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-0 :as nb-status-0]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def state {:id                      (str nbs/resource-type "/uuid")
            :resource-type           nbs/resource-type
            :created                 timestamp
            :updated                 timestamp

            :acl                     valid-acl

            :version                 0
            :parent                  "nuvlabox/uuid"
            :status                  "OPERATIONAL"
            :comment                 "some witty comment"

            :next-heartbeat          timestamp
            :current-time            timestamp

            :resources               {:cpu       {:capacity 8
                                                  :load     4.5
                                                  :topic    "topic/name"}
                                      :ram       {:capacity   4096
                                                  :used       1000
                                                  :raw-sample "{\"one\": 1}"}
                                      :disks     [{:device     "root"
                                                   :capacity   20000
                                                   :topic      "topic/name"
                                                   :raw-sample "{\"one\": 1}"
                                                   :used       10000}
                                                  {:device   "datastore"
                                                   :capacity 20000
                                                   :used     10000}]
                                      :net-stats [{:interface         "eth0"
                                                   :bytes-received    5247943
                                                   :bytes-transmitted 41213
                                                   }
                                                  {:interface         "vpn"
                                                   :bytes-received    2213
                                                   :bytes-transmitted 55}]
                                      :power-consumption  [{:metric-name "IN_current"
                                                            :energy-consumption 2.4
                                                            :unit "A"}
                                                           {:metric-name "IN_voltage"
                                                            :energy-consumption 220
                                                            :unit"V"}]}

            :peripherals             {:usb [{:vendor-id   "vendor-id"
                                             :device-id   "device-id"
                                             :bus-id      "bus-id"
                                             :product-id  "product-id"
                                             :description "description"}]}

            :wifi-password           "some-secure-password"
            :nuvlabox-api-endpoint   "https://1.2.3.4:1234"
            :operating-system        "Ubuntu"
            :architecture            "x86"
            :hostname                "localhost"
            :ip                      "127.0.0.1"
            :docker-server-version   "19.0.3"
            :last-boot               "2020-02-18T19:42:08Z"
            :inferred-location       [46.2044 6.1432 373.]
            :gpio-pins               [{:name    "GPIO. 7"
                                       :bcm     4
                                       :mode    "IN"
                                       :voltage 1
                                       :pin     7}
                                      {:pin 1}]
            :nuvlabox-engine-version "1.2.3"
            :docker-plugins          ["sixsq/img-authz-plugin:arm64"]
            :vulnerabilities         {:summary {:total 1920
                                                :affected-products  ["openssh", "go"]
                                                :average-score  6.1}
                                      :items  [{:vulnerability-id "CVE-X-Y-Z"
                                                :vulnerability-description "test threat"
                                                :product "OpenSSH"
                                                :vulnerability-reference "url"}
                                               {:vulnerability-id "CVE-X-Y-Z2"
                                                :product "Apache"
                                                :vulnerability-score 5.7}]}})


(deftest check-nuvlabox-status

  (stu/is-valid ::nb-status-0/schema state)
  (stu/is-invalid ::nb-status-0/schema (assoc state :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :status}]
    (stu/is-invalid ::nb-status-0/schema (dissoc state attr)))

  ;; optional
  (doseq [attr #{:next-heartbeat :current-time :resources :peripherals :wifi-password :comment :nuvlabox-api-endpoint
                 :inferred-location :gpio-pins :nuvlabox-engine-version :docker-plugins :vulnerabilities
                 :power-consumption}]
    (stu/is-valid ::nb-status-0/schema (dissoc state attr))))
