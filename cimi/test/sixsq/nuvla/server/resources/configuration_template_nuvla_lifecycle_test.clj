(ns sixsq.nuvla.server.resources.configuration-template-nuvla-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context ct/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-type)
  (mdtu/check-metadata-exists (str ct/resource-type "-" ct-nuvla/service)))


(deftest retrieve-by-id
  (test-utils/check-retrieve-by-id ct-nuvla/service))


(deftest lifecycle
  (test-utils/check-lifecycle ct-nuvla/service))


(deftest bad-methods
  (test-utils/check-bad-methods))
