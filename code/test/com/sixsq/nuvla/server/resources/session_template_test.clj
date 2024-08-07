(ns com.sixsq.nuvla.server.resources.session-template-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.session-template :as st]
    [com.sixsq.nuvla.server.resources.session-template-api-key :as api-key]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" api-key/resource-url)))


(deftest check-metadata-contents
  (let [{:keys [attributes capabilities actions]}
        (mdtu/get-generated-metadata (str st/resource-type "-" api-key/resource-url))

        value-scope-count (->> attributes
                               (map :value-scope)
                               count)]

    (is (nil? actions))
    (is (nil? capabilities))
    (is attributes)
    (is (pos? value-scope-count))))
