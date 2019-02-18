(ns sixsq.nuvla.server.resources.session-template-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-api-key :as api-key]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" api-key/resource-url)))


(deftest check-metadata-contents
  (let [{:keys [attributes vscope capabilities actions]}
        (mdtu/get-generated-metadata (str st/resource-type "-" api-key/resource-url))]

    (is (nil? actions))
    (is (nil? capabilities))
    (is attributes)
    (is (pos? (count vscope)))))
