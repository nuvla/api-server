(ns sixsq.nuvla.db.binding-lifecycle
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.binding :as db]
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]))

(s/def ::id string?)
(s/def ::long int?)
(s/def ::boolean boolean?)
(s/def ::string string?)

(s/def ::resource (s/keys :req-un [::id ::long ::boolean ::string]))

(s/def ::acl ::acl-resource/acl)

(def admin-acl {:owners ["group/nuvla-admin"]})

(defn check-binding-lifecycle [db-impl]
  (with-open [db db-impl]

    (let [collection-id "my-collection"]

      ;; initialize the database
      (db/initialize db collection-id {:spec ::resource})

      ;; create an entry in the database
      (let [my-id (str collection-id "/my-uuid")
            my-data {:id my-id, :long 1, :boolean true, :string "ok"}
            my-data-with-acl (assoc my-data :acl admin-acl)
            response (db/add db my-data-with-acl nil)]
        (is (s/valid? ::resource my-data-with-acl))
        (is (= 201 (:status response)))
        (is (= my-id (get-in response [:headers "Location"])))

        ;; ensure that the entry can be retrieved
        (let [retrieved-data (db/retrieve db my-id nil)]
          (is (= my-data-with-acl retrieved-data)))

        ;; check that it shows up in a query
        (let [[query-meta query-hits] (db/query db collection-id {:nuvla/authn auth/internal-identity})]
          (is (= 1 (:count query-meta)))
          (is (= my-data-with-acl (first query-hits))))

        ;; add a second entry
        (let [my-id-2 (str collection-id "/my-uuid-2")
              my-data-2 {:id my-id-2, :long 2, :boolean false, :string "nok"}
              my-data-2-with-acl (assoc my-data-2 :acl admin-acl)
              response (db/add db my-data-2-with-acl nil)]
          (is (s/valid? ::resource my-data-2-with-acl))
          (is (= 201 (:status response)))
          (is (= my-id-2 (get-in response [:headers "Location"])))

          ;; ensure that is can be retrieved (and flush index for elasticsearch)
          (let [retrieved-data (db/retrieve db my-id-2 nil)]
            (is (= my-data-2-with-acl retrieved-data)))

          ;; check that query has another entry
          (let [[query-meta query-hits] (db/query db collection-id {:nuvla/authn auth/internal-identity})]
            (is (= 2 (:count query-meta)))
            (is (= #{my-id my-id-2} (set (map :id query-hits)))))

          ;; adding the same entry again must fail
          (let [response (db/add db {:id my-id} nil)]
            (is (= 409 (:status response))))

          ;; update the entry
          (let [updated-data (assoc my-data-with-acl :two "3")
                response (db/edit db updated-data nil)]
            (is (= 200 (:status response)))

            ;; make sure that the update was applied
            (let [retrieved-data (db/retrieve db my-id nil)]
              (is (= updated-data retrieved-data)))

            ;; delete the first entry
            (let [response (db/delete db updated-data nil)]
              (is (= 200 (:status response))))

            ;; delete the second entry
            (let [response (db/delete db {:id my-id-2} nil)]
              (is (= 200 (:status response))))

            ;; deleting the first one a second time should give a 404
            (try
              (db/delete db updated-data nil)
              (is (nil? "delete of non-existent resource did not throw an exception"))
              (catch Exception e
                (let [response (ex-data e)]
                  (is (= 404 (:status response)))))))

          ;; also retrieving it should do the same
          (try
            (db/retrieve db my-id nil)
            (is (nil? "retrieve of non-existent resource did not throw an exception"))
            (catch Exception e
              (let [response (ex-data e)]
                (is (= 404 (:status response))))))
          )))))
