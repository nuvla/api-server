(ns sixsq.nuvla.server.resources.connector-docker-lifecycle-test
    (:require
    [clojure.test :refer :all]

    [sixsq.nuvla.server.resources.connector-template-docker :as ctd]
    [sixsq.nuvla.server.resources.connector-test-utils :as tu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)

(deftest lifecycle
    (tu/connector-lifecycle ctd/cloud-service-type))
