(ns com.sixsq.slipstream.ssclj.resources.spec.user-identifier-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.resources.spec.user-identifier :as user-identifier]
    [com.sixsq.slipstream.ssclj.resources.user-identifier :as ui]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id          (str ui/resource-url "/hash-of-identifier")
             :resourceURI ui/resource-uri
             :created     timestamp
             :updated     timestamp
             :acl         valid-acl

             :identifier  "some-long-identifier"
             :user        {:href "user/35413_univ_lyon1_frurn_mace_cru_fr_federation_univ_lyon1_fr_https___fed_id_nuv_la_samlbridge_module_php_saml_sp_metadata_php_sixsq_saml_bridge_umef2do_i7rkfnhwwkq6fxwhx9u_"}}]

    (stu/is-valid ::user-identifier/schema cfg)
    (stu/is-invalid ::user-identifier/schema (assoc cfg :bad-attr "BAD_ATTR"))

    (doseq [attr #{:id :resourceURI :created :updated :acl :identifier :user}]
      (stu/is-invalid ::user-identifier/schema (dissoc cfg attr)))

    (doseq [attr #{:username :server :clientIP}]
      (stu/is-valid ::user-identifier/schema (dissoc cfg attr)))))
