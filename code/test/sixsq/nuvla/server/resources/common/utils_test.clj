(ns sixsq.nuvla.server.resources.common.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.common.utils :as t]
    [sixsq.nuvla.server.util.time :as time]))

(deftest check-expired?-and-not-expired?
  (let [past-time (time/to-str (time/ago 10 :minutes))
        future-time (time/to-str (time/from-now 10 :minutes))]
    (is (false? (t/expired? nil)))
    (is (true? (t/expired? past-time)))
    (is (false? (t/expired? future-time)))
    (is (true? (t/not-expired? nil)))
    (is (false? (t/not-expired? past-time)))
    (is (true? (t/not-expired? future-time)))))

(deftest check-select-desc-keys
  (let [resource {:name        "name"
                  :description "description"
                  :tags        #{"one", "two"}}]
    (is (= resource (t/select-desc-keys resource)))
    (is (= resource (t/select-desc-keys (assoc resource :other "ignored"))))
    (is (= (dissoc resource :name) (t/select-desc-keys (dissoc resource :name))))))


(deftest check-id-utils
  (are [expected id] (= expected (t/parse-id id))
                     nil nil
                     nil 47
                     ["" nil] ""
                     ["cloud-entry-point" nil] "cloud-entry-point"
                     ["resource" "uuid"] "resource/uuid"
                     ["resource" "uuid"] "resource/uuid/ignored")

  (are [expected id] (= expected (t/resource-from-id id))
                     nil nil
                     nil 47
                     "" ""
                     "cloud-entry-point" "cloud-entry-point"
                     "resource" "resource/uuid"
                     "resource" "resource/uuid/ignored")

  (are [expected id] (= expected (t/uuid-from-id id))
                     nil nil
                     nil 47
                     nil ""
                     nil "cloud-entry-point"
                     "uuid" "resource/uuid"
                     "uuid" "resource/uuid/ignored"))
