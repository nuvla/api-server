(ns com.sixsq.nuvla.server.resources.session-template-mitreid-token-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.session-template :as st]
    [com.sixsq.nuvla.server.resources.session-template-mitreid-token :as mitreid-token]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))



(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" mitreid-token/resource-url)))


;; FIXME: There should be a lifecycle test!
