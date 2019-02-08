(ns sixsq.nuvla.server.resources.user-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context user/resource-type))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id user/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
