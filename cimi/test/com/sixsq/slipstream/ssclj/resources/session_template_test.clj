(ns com.sixsq.slipstream.ssclj.resources.session-template-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.session-template :as st]
    [com.sixsq.slipstream.ssclj.resources.session-template-api-key :as api-key]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-url "-" api-key/resource-url)))


(deftest check-metadata-contents
  (let [{:keys [attributes vscope capabilities actions]}
        (mdtu/get-generated-metadata (str st/resource-url "-" api-key/resource-url))]

    (is (nil? actions))
    (is (nil? capabilities))
    (is attributes)
    (is (pos? (count vscope)))))
