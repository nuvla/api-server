(ns sixsq.nuvla.server.resources.credential-lifecycle-test
  (:require
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context credential/resource-type))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id credential/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
