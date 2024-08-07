(ns com.sixsq.nuvla.server.resources.data-object-generic-lifecycle-test
  (:require
    [clojure.test :refer [deftest join-fixtures use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.data-object :as data-obj]
    [com.sixsq.nuvla.server.resources.data-object-generic :as data-obj-generic]
    [com.sixsq.nuvla.server.resources.data-object-lifecycle-test-utils :as do-ltu]
    [com.sixsq.nuvla.server.resources.data-object-template :as data-obj-tpl]
    [com.sixsq.nuvla.server.resources.data-object-template-generic :as data-obj-tpl-generic]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


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
   :location     [0.0 0.0 0.0]})


(deftest check-metadata
  (mdtu/check-metadata-exists data-obj-generic/resource-type))


(deftest lifecycle
  (do-ltu/full-eo-lifecycle (str p/service-context data-obj-tpl/resource-type "/" data-obj-tpl-generic/data-object-type)
                            (data-object)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id data-obj/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))


