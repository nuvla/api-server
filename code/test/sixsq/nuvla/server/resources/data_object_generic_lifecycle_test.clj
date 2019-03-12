(ns sixsq.nuvla.server.resources.data-object-generic-lifecycle-test
  (:require
    [clojure.test :refer [deftest join-fixtures use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.data-object :as data-obj]
    [sixsq.nuvla.server.resources.data-object-lifecycle-test-utils :as do-ltu]
    [sixsq.nuvla.server.resources.data-object-template :as data-obj-tpl]
    [sixsq.nuvla.server.resources.data-object-template-generic :as data-obj-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))


(use-fixtures :once (join-fixtures [ltu/with-test-server-fixture
                                    do-ltu/create-s3-credential!
                                    do-ltu/s3-redefs!]))


(def base-uri (str p/service-context data-obj/resource-type))


(defn data-object
  []
  {:bucket  "my-bucket"
   :credential   do-ltu/*s3-credential-id*
   :content-type "application/gzip"
   :object  "my/obj/name-1"})


(deftest lifecycle
  (do-ltu/full-eo-lifecycle (str p/service-context data-obj-tpl/resource-type "/" data-obj-generic/type)
                            (data-object)))


#_(deftest bad-methods
    (let [resource-uri (str p/service-context (u/new-resource-id data-obj/resource-type))]
      (ltu/verify-405-status [[base-uri :options]
                              [base-uri :delete]
                              [resource-uri :options]
                              [resource-uri :post]])))


