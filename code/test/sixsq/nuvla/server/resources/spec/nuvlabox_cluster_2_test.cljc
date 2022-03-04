(ns sixsq.nuvla.server.resources.spec.nuvlabox-cluster-2-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-cluster :as nb-cluster]
    [sixsq.nuvla.server.resources.spec.nuvlabox-cluster-2 :as nb-cluster-2]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def cluster {:id            (str nb-cluster/resource-type "/uuid")
              :resource-type nb-cluster/resource-type
              :name          "cluster 1234abcd"
              :description   "a NB cluster with X nodes"
              :created       timestamp
              :updated       timestamp

              :acl           valid-acl

              :version       2

              :cluster-id    "1234abcd"
              :workers       ["12nb12hb"]
              :managers      ["absafhwe"]
              :nuvlabox-workers         ["nuvlabox/123-456-abc-def-worker"]
              :nuvlabox-managers        ["nuvlabox/123-456-abc-def-manager"]
              :orchestrator  "swarm"
              :status-notes  ["message 1" "comment A" ""]})


(deftest check-nuvlabox-cluster

  (stu/is-valid ::nb-cluster-2/schema cluster)
  (stu/is-invalid ::nb-cluster-2/schema (assoc cluster :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :cluster-id :orchestrator :managers}]
    (stu/is-invalid ::nb-cluster-2/schema (dissoc cluster attr)))

  ;; optional
  (doseq [attr #{:workers :nuvlabox-workers :nuvlabox-managers :status-notes}]
    (stu/is-valid ::nb-cluster-2/schema (dissoc cluster attr))))
