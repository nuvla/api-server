(ns sixsq.nuvla.server.resources.spec.notification-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.notification :refer :all]
    [sixsq.nuvla.server.resources.spec.notification :as notification]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def timestamp "2015-01-16T08:05:00.00Z")


(def valid-notification
  {:id                "notification/1234567890"
   :resource-type     resource-type
   :created           timestamp
   :updated           timestamp
   :acl               {:owners   ["user/joe"]
                       :view-acl ["group/nuvla-anon"]}

   :message           "message"
   :category          "type"
   :content-unique-id "content-unique-id"

   :target-resource   "foo/bar"
   :not-before        timestamp
   :expiry            timestamp
   :callback          "foo/bar"
   :callback-msg      ""})


(deftest check-message
  (stu/is-invalid ::notification/schema (assoc valid-notification :message "")))


(deftest check-category
  (doseq [valid-category ["z" "foo" "bar-baz"]]
    (stu/is-valid ::notification/schema (assoc valid-notification :category valid-category)))
  (doseq [invalid-category ["" "Z" "Foo" "BaR-BaZ"]]
    (stu/is-invalid ::notification/schema (assoc valid-notification :category invalid-category))))


(deftest check-msg-unique-id
  (stu/is-invalid ::notification/schema (assoc valid-notification :content-unique-id "")))


(deftest check-reference
  (let [updated-notification (assoc valid-notification :target-resource "another/valid-identifier")]
    (stu/is-valid ::notification/schema updated-notification))
  (let [updated-notification (assoc valid-notification :target-resource "/not a valid reference/")]
    (stu/is-invalid ::notification/schema updated-notification)))


(deftest check-callback
  (let [updated-notification (assoc valid-notification :callback "another/valid-identifier")]
    (stu/is-valid ::notification/schema updated-notification))
  (let [updated-notification (assoc valid-notification :callback "/not a valid reference/")]
    (stu/is-invalid ::notification/schema updated-notification)))


(deftest check-notification-schema

  (stu/is-valid ::notification/schema valid-notification)

  ;; mandatory keywords
  (doseq [k #{:id :resource-type :acl :message :category :content-unique-id}]
    (stu/is-invalid ::notification/schema (dissoc valid-notification k)))

  ;; optional keywords
  (doseq [k #{:terget-resource :not-before :expiry :callback :callback-msg}]
    (stu/is-valid ::notification/schema (dissoc valid-notification k))))
