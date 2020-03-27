(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-0-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nbs]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-0 :as nb-status-0]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def state {:id                    (str nbs/resource-type "/uuid")
            :resource-type         nbs/resource-type
            :created               timestamp
            :updated               timestamp

            :acl                   valid-acl

            :version               0
            :parent                "nuvlabox/uuid"
            :status                "OPERATIONAL"
            :comment               "some witty comment"

            :next-heartbeat        timestamp
            :current-time          timestamp

            :resources             {:cpu   {:capacity 8
                                            :load     4.5
                                            :topic    "topic/name"}
                                    :ram   {:capacity   4096
                                            :used       1000
                                            :raw-sample "{\"one\": 1}"}
                                    :disks [{:device     "root"
                                             :capacity   20000
                                             :topic      "topic/name"
                                             :raw-sample "{\"one\": 1}"
                                             :used       10000}
                                            {:device   "datastore"
                                             :capacity 20000
                                             :used     10000}]
                                    :net-stats [{:interface "eth0"
                                                 :bytes-received    5247943
                                                 :bytes-transmitted 41213
                                                 }
                                                {:interface "vpn"
                                                 :bytes-received    2213
                                                 :bytes-transmitted 55}]}

            :peripherals           {:usb [{:vendor-id   "vendor-id"
                                           :device-id   "device-id"
                                           :bus-id      "bus-id"
                                           :product-id  "product-id"
                                           :description "description"}]}

            :wifi-password         "some-secure-password"
            :nuvlabox-api-endpoint "https://1.2.3.4:1234"
            :operating-system      "Ubuntu"
            :architecture          "x86"
            :hostname              "localhost"
            :ip                    "127.0.0.1"
            :docker-server-version "19.0.3"
            :last-boot             "2020/02/18, 19:42:08"})


(deftest check-nuvlabox-status

  (stu/is-valid ::nb-status-0/schema state)
  (stu/is-invalid ::nb-status-0/schema (assoc state :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :status}]
    (stu/is-invalid ::nb-status-0/schema (dissoc state attr)))

  ;; optional
  (doseq [attr #{:next-heartbeat :current-time :resources :peripherals :wifi-password :comment :nuvlabox-api-endpoint}]
    (stu/is-valid ::nb-status-0/schema (dissoc state attr))))
