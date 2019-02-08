(ns sixsq.nuvla.server.resources.session-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context session/resource-type))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id session/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]])))
