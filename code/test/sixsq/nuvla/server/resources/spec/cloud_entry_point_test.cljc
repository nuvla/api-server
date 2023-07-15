(ns sixsq.nuvla.server.resources.spec.cloud-entry-point-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.cloud-entry-point :as t]
    [sixsq.nuvla.server.resources.spec.cloud-entry-point :as cep]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-root-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id            t/resource-type
                   :resource-type p/service-context
                   :created       timestamp
                   :updated       timestamp
                   :acl           t/resource-acl
                   :base-uri      "http://cloud.example.org/"
                   :collections   {:collection-alpha {:href "resource/alpha"}
                                   :collection-beta  {:href "resource/beta"}}}]

    (stu/is-valid ::cep/resource root)

    (stu/is-invalid ::cep/resource (assoc root :collections {}))

    (doseq [attr #{:id :resource-type :created :updated :acl :base-uri}]
      (stu/is-invalid ::cep/resource (dissoc root attr)))

    (doseq [attr #{:collections}]
      (stu/is-valid ::cep/resource (dissoc root attr)))))
