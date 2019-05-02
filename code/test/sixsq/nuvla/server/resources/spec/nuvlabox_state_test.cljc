(ns sixsq.nuvla.server.resources.spec.nuvlabox-state-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-state :as nbs]
    [sixsq.nuvla.server.resources.spec.nuvlabox-state :as nuvlabox-state]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-state {:id            (str nbs/resource-type "/uuid")
                     :resource-type nbs/resource-type
                     :created       timestamp
                     :updated       timestamp
                     :nextCheck     timestamp
                     :acl           valid-acl

                     :nuvlabox      {:href "nuvlabox-resource/uuid"}

                     :cpu           24
                     :ram           {:capacity 4096
                                     :used     1000}
                     :disks         {:root      {:capacity 20000
                                                 :used     10000}
                                     :datastore {:capacity 20000
                                                 :used     10000}}
                     :usb           [{:busy        false
                                      :vendor-id   "vendor-id"
                                      :device-id   "device-id"
                                      :bus-id      "bus-id"
                                      :product-id  "product-id"
                                      :description "description"}]})


(def nano-state {:id                  (str nbs/resource-type "/uuid")
                 :resource-type       nbs/resource-type
                 :created             timestamp
                 :updated             timestamp
                 :nextCheck           timestamp
                 :acl                 valid-acl

                 :nuvlabox            {:href "nuvlabox-resource/uuid"}

                 :cpu                 4
                 :ram                 {:capacity 4096
                                       :used     1000}
                 :disks               {:root {:capacity 20000
                                              :used     10000}}
                 :usb                 [{:busy        false
                                        :vendor-id   "vendor-id"
                                        :device-id   "device-id"
                                        :bus-id      "bus-id"
                                        :product-id  "product-id"
                                        :description "description"}]

                 :swarmNodeId         "svl7sjjvxv6gk8z0aolupl4af"
                 :swarmManagerId      ["pdj23jccbxw8nxa7nzbfd2123", "n23uhdb238dwb72y239dh1812e"]
                 :swarmManagerToken   "SWMTKN-1-5xugkmxsgbnrd4hm1073zvjjf659huswgrouhk19seh8gtxtwa-3n8qk8dairm1gk5tciepb0ba3"
                 :mutableWifiPassword "aNewWifiPassword"
                 :swarmNode           "nodename"
                 :leader?             true
                 :swarmWorkerToken    "SWMTKN-1-5mzrn8ucjcsqehush0biigfw53kxtky0twy8xwf7esamvq5gkg-eqaqs5e875yg3p0kbk2v14k2h"
                 :tlsCA               "tls ca"
                 :tlsCert             "tls cert"
                 :tlsKey              "tld key"})


(deftest check-nuvlabox-state

  (doseq [state [nuvlabox-state nano-state]]

    (stu/is-valid ::nuvlabox-state/nuvlabox-state state)
    (stu/is-invalid ::nuvlabox-state/nuvlabox-state (assoc state :bad-attr "BAD_ATTR"))

    ;; required
    (doseq [attr #{:id :resource-type :created :updated :acl
                   :nuvlabox :nextCheck :cpu :ram :disks :usb}]
      (stu/is-invalid ::nuvlabox-state/nuvlabox-state (dissoc state attr)))

    ;; optional
    (doseq [attr #{:mutableWifiPassword :swarmManagerId
                   :swarmNodeId :swarmManagerToken :swarmNode :leader? :swarmWorkerToken
                   :tlsCA :tlsCert :tlsKey}]
      (stu/is-valid ::nuvlabox-state/nuvlabox-state (dissoc state attr)))))
