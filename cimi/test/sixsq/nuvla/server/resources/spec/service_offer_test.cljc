(ns sixsq.nuvla.server.resources.spec.service-offer-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.service-offer :as so-resource]
    [sixsq.nuvla.server.resources.spec.service-offer :as so]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest check-ServiceInfo
  (let [timestamp "1964-08-25T10:00:00.0Z"
        service-offer {:id          (str so-resource/resource-url "/offer-uuid")
                       :resource-type so-resource/resource-uri
                       :created     timestamp
                       :updated     timestamp
                       :acl         valid-acl
                       :connector   {:href "myconnector"}
                       :other       "value"}]

    (stu/is-valid ::so/service-offer service-offer)

    (stu/is-valid ::so/service-offer (assoc-in service-offer [:connector :href] "connector/my-full-id"))

    ;; mandatory keywords
    (doseq [k #{:created :updated :acl :connector}]
      (stu/is-invalid ::so/service-offer (dissoc service-offer k)))

    ;; optional keywords
    (doseq [k #{:other}]
      (stu/is-valid ::so/service-offer (dissoc service-offer k)))))
