(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-2-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nbs]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-2 :as nb-status-2]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def state {:id                      (str nbs/resource-type "/uuid")
            :resource-type           nbs/resource-type
            :created                 timestamp
            :updated                 timestamp

            :acl                     valid-acl

            :version                 2
            :parent                  "nuvlabox/uuid"
            :status                  "OPERATIONAL"

            :next-heartbeat          timestamp
            :current-time            timestamp

            :resources               {:cpu               {:capacity 8
                                                          :load     4.5
                                                          :load-1   4.3
                                                          :load-5   5.5
                                                          :system-calls   0
                                                          :interrupts     13621648
                                                          :software-interrupts  37244
                                                          :context-switches     382731}
                                      :ram               {:capacity 4096
                                                          :used     1000}
                                      :disks             [{:device   "root"
                                                           :capacity 20000
                                                           :used     10000}
                                                          {:device   "datastore"
                                                           :capacity 20000
                                                           :used     10000}]
                                      :net-stats         [{:interface         "eth0"
                                                           :bytes-received    5247943
                                                           :bytes-transmitted 41213
                                                           }
                                                          {:interface         "vpn"
                                                           :bytes-received    2213
                                                           :bytes-transmitted 55}]
                                      :power-consumption [{:metric-name        "IN_current"
                                                           :energy-consumption 2.4
                                                           :unit               "A"}]}

            :wifi-password           "some-secure-password"
            :nuvlabox-api-endpoint   "https://4.3.2.1:4321"
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
            :docker-plugins          []
            :vulnerabilities         {}
            :node-id                "xyz"
            :cluster-id             "123xyz"
            :installation-parameters {:config-files ["docker-compose.yml",
                                                     "docker-compose.usb.yaml"]
                                      :working-dir  "/home/user"
                                      :project-name "nuvlabox"
                                      :environment  []}
            :jobs                    ["job/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                                      "job/d2dc1733-ac2c-45b1-b68a-0ec02653bc0d"]
            :swarm-node-cert-expiry-date "2020-02-18T19:42:08Z"
            :online                      true
            :host-user-home           "/home/user"
            :cluster-node-role        "manager"
            :status-notes             ["Lost quorum", "Swap disabled"]
            :cluster-nodes            ["syz", "xyz", "1dsdr3"]
            :cluster-managers         ["syz"]
            :cluster-join-address     "194.182.171.166:2377"
            :orchestrator             "swarm"})


(deftest check-nuvlabox-status

  (stu/is-valid ::nb-status-2/schema state)
  (stu/is-invalid ::nb-status-2/schema (assoc state :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :status}]
    (stu/is-invalid ::nb-status-2/schema (dissoc state attr)))

  ;; optional
  (doseq [attr #{:next-heartbeat :current-time :resources :wifi-password :comment
                 :inferred-location :nuvlabox-api-endpoint :gpio-pins :nuvlabox-engine-version
                 :docker-plugins :vulnerabilities :swarm-node-id :installation-parameters
                 :power-consumption ::jobs :swarm-node-cert-expiry-date :online :host-user-home
                 :cluster-id :cluster-node-role :status-notes :cluster-nodes :cluster-managers :orchestrator
                 :cluster-join-address}]
    (stu/is-valid ::nb-status-2/schema (dissoc state attr))))
