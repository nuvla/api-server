(ns sixsq.nuvla.server.resources.user-params-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user-params :as up]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase up/resource-name)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id up/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]])))
