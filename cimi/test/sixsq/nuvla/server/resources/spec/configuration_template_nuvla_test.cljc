(ns sixsq.nuvla.server.resources.spec.configuration-template-nuvla-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-nuvla :as nuvla]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ADMIN"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest check-configuration-template-nuvla
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str ct/resource-type "/" nuvla/service)
             :resource-type nuvla/service
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :service       nuvla/service

             :smtp-username "username"
             :smtp-password "password"
             :smtp-host     "host"
             :smtp-port     465
             :smtp-ssl      true
             :smtp-debug    true

             :support-email "admin@example.org"}]

    (stu/is-valid ::ct-nuvla/schema cfg)

    ;; mandatory keys
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::ct-nuvla/schema (dissoc cfg k)))

    ;; optional keys
    (doseq [k #{:smtp-username :smtp-password :smtp-host :smtp-port :smtp-ssl :smtp-debug :support-email}]
      (stu/is-valid ::ct-nuvla/schema (dissoc cfg k)))))
