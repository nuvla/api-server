(ns sixsq.nuvla.server.resources.credential-cloud-docker-lifecycle-test
  (:require
    [clojure.test :refer [are deftest is use-fixtures]]
    [sixsq.nuvla.server.resources.connector-template-docker :as cont]
    [sixsq.nuvla.server.resources.credential-cloud-lifecycle-test-utils :as cclt]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-cloud-docker :as cloud-docker]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)

(deftest lifecycle
  (cclt/cloud-cred-lifecycle {:href      (str ct/resource-type "/" cloud-docker/method)
                              :key       "key"
                              :secret    "secret"
                              :quota     7
                              :connector {:href "connector/foo-bar-baz"}}
                             cont/cloud-service-type))
