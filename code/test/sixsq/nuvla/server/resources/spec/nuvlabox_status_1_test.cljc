(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-1-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nbs]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-1 :as nb-status-1]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def state {:id             (str nbs/resource-type "/uuid")
            :resource-type  nbs/resource-type
            :created        timestamp
            :updated        timestamp

            :acl            valid-acl

            :version        1
            :parent         "nuvlabox/uuid"
            :status         "OPERATIONAL"
            :comment        "some witty comment"

            :next-heartbeat timestamp
            :current-time   timestamp

            :resources      {:cpu   {:capacity 8
                                     :load     4.5}
                             :ram   {:capacity 4096
                                     :used     1000}
                             :disks [{:device   "root"
                                      :capacity 20000
                                      :used     10000}
                                     {:device   "datastore"
                                      :capacity 20000
                                      :used     10000}]}

            :wifi-password  "some-secure-password"
            :nuvlabox-api-endpoint  "https://4.3.2.1:4321"})


(deftest check-nuvlabox-status

  (stu/is-valid ::nb-status-1/schema state)
  (stu/is-invalid ::nb-status-1/schema (assoc state :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :status}]
    (stu/is-invalid ::nb-status-1/schema (dissoc state attr)))

  ;; optional
  (doseq [attr #{:next-heartbeat :current-time :resources :wifi-password :comment :nuvlabox-api-endpoint}]
    (stu/is-valid ::nb-status-1/schema (dissoc state attr))))
