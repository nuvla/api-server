(ns sixsq.nuvla.server.resources.session-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context st/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists st/resource-type))


(deftest bad-methods
  (stu/bad-methods base-uri))
