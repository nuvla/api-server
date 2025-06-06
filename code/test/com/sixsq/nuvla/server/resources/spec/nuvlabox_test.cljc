(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))




(deftest check-coe-resource-actions-body
  (stu/is-invalid ::nb/coe-resource-actions-body {})
  (stu/is-valid ::nb/coe-resource-actions-body {:docker []})
  (stu/is-valid ::nb/coe-resource-actions-body {:docker [{:action "pull" :resource "image" :id "hello-world" :credential "credential/123-abc"}
                                                         {:action "remove" :resource "volume" :id "some-volume"}
                                                         {:action "remove" :resource "network" :id "some-net"}
                                                         {:action "remove" :resource "container" :id "some-container"}
                                                         {:action "remove" :resource "container" :id "some-container"}]}))
