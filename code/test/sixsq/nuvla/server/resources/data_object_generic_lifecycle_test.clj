(ns sixsq.nuvla.server.resources.data-object-generic-lifecycle-test
  (:require
    [clojure.test :refer [deftest join-fixtures use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as data-obj]
    [sixsq.nuvla.server.resources.data-object-generic :as data-obj-generic]
    [sixsq.nuvla.server.resources.data-object-lifecycle-test-utils :as do-ltu]
    [sixsq.nuvla.server.resources.data-object-template :as data-obj-tpl]
    [sixsq.nuvla.server.resources.data-object-template-generic :as data-obj-tpl-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once (join-fixtures [ltu/with-test-server-fixture
                                    do-ltu/create-s3-credential!
                                    do-ltu/s3-redefs!]))


(def base-uri (str p/service-context data-obj/resource-type))


(defn data-object
  []
  {:credential   do-ltu/*s3-credential-id*
   :bucket       "my-bucket"
   :object       "my/obj/name-1"

   :content-type "application/gzip"
   :bytes        42
   :md5sum       "3deb5ba5d971c85dd979b7466debfdee"
   :timestamp    "1964-08-25T10:00:00.00Z"
   :location     {:lon 0.0
                  :lat 0.0
                  :alt 0.0}})


(deftest check-metadata
  (mdtu/check-metadata-exists data-obj-generic/resource-type))


(deftest lifecycle
  (do-ltu/full-eo-lifecycle (str p/service-context data-obj-tpl/resource-type "/" data-obj-tpl-generic/data-object-type)
                            (data-object)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id data-obj/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))


