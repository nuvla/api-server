(ns com.sixsq.slipstream.ssclj.resources.external-object-generic-lifecycle-test
  (:require
    [clojure.test :refer [deftest join-fixtures use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-lifecycle-test-utils :as eoltu]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-generic :as generic]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))


(use-fixtures :each (join-fixtures [ltu/with-test-server-fixture
                                    eoltu/create-connector-fixture!
                                    eoltu/create-cloud-cred-fixture!
                                    eoltu/s3-redefs!]))

(def base-uri (str p/service-context (u/de-camelcase eo/resource-name)))

(defn external-object
  []
  {:bucketName      "my-bucket"
   :objectStoreCred {:href eoltu/*cred-uri*}
   :contentType     "application/gzip"
   :objectName      "my/obj/name-1"})


(deftest lifecycle
  (eoltu/full-eo-lifecycle (str p/service-context eot/resource-url "/" generic/objectType)
                           (external-object)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id eo/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))


