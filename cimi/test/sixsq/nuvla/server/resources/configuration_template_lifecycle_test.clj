(ns sixsq.nuvla.server.resources.configuration-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-slipstream :as slipstream]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context ct/resource-type))


(deftest retrieve-by-id
  (test-utils/check-retrieve-by-id slipstream/service))

(deftest lifecycle
  (test-utils/check-lifecycle slipstream/service))

(deftest bad-methods
  (test-utils/check-bad-methods))
