(ns com.sixsq.nuvla.server.resources.spec.common-body-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.common-body :as t]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-nonblank-string
  (doseq [v [{:doc {:tags []}}
             {:doc {:tags ["foo"]}}
             {:doc {:tags ["foo" "bar"]}}]]
    (stu/is-valid ::t/bulk-edit-tags-body v))

  (doseq [v [nil
             {}
             {:hello "x"}
             {:doc {:something-else 1}}
             {:doc {:tags ["foo"] :foo 1}}]]
    (stu/is-invalid ::t/bulk-edit-tags-body v)))
