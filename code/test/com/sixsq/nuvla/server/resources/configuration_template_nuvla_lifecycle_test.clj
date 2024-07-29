(ns com.sixsq.nuvla.server.resources.configuration-template-nuvla-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.configuration-template-lifecycle-test-utils :as test-utils]
    [com.sixsq.nuvla.server.resources.configuration-template-nuvla :as ct-nuvla]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context ct/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-type
                              (str ct/resource-type "-" ct-nuvla/service)
                              (str ct/resource-type "-" ct-nuvla/service "-create")))


(deftest retrieve-by-id
  (test-utils/check-retrieve-by-id ct-nuvla/service))


(deftest lifecycle
  (test-utils/check-lifecycle ct-nuvla/service))


(deftest bad-methods
  (test-utils/check-bad-methods))
