(ns sixsq.nuvla.db.binding-lifecycle
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [is]]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.binding :as db]
<<<<<<< Updated upstream
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]))
=======
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]
    [sixsq.nuvla.server.util.response :as r]))
>>>>>>> Stashed changes


(s/def ::id string?)


(s/def ::long int?)


(s/def ::boolean boolean?)


(s/def ::string string?)


(s/def ::resource (s/keys :req-un [::id ::long ::boolean ::string]))


(s/def ::acl ::acl-resource/acl)


(def admin-acl {:owners ["group/nuvla-admin"]})

(defn try-call
  [f]
  (try
    (f)
    (catch Exception e
      (ex-data e))))


(defn check-binding-lifecycle [db-impl]
  (with-open [db db-impl]

    (let [collection-id "my-collection"]

      ;; initialize the database
      (db/initialize db collection-id {:spec ::resource})

      ;; create an entry in the database
      (let [my-id            (str collection-id "/my-uuid")
            my-data          {:id my-id, :long 1, :boolean true, :string "ok"}
            my-data-with-acl (assoc my-data :acl admin-acl :more "hi")
            response         (db/add db my-data-with-acl nil)]
        (is (s/valid? ::resource my-data-with-acl))
        (is (r/status-created? response))
        (is (= my-id (get-in response [:headers "Location"])))

        ;; ensure that the entry can be retrieved
        (let [retrieved-data (db/retrieve db my-id nil)]
          (is (= my-data-with-acl retrieved-data)))

        ;; check that it shows up in a query
        (let [[query-meta query-hits] (db/query db collection-id {:nuvla/authn auth/internal-identity})]
          (is (= 1 (:count query-meta)))
          (is (= my-data-with-acl (first query-hits))))

        ;; add a second entry
        (let [my-id-2            (str collection-id "/my-uuid-2")
              my-data-2          {:id my-id-2, :long 2, :boolean false, :string "nok"}
              my-data-2-with-acl (assoc my-data-2 :acl admin-acl)
              response           (db/add db my-data-2-with-acl nil)]
          (is (s/valid? ::resource my-data-2-with-acl))
          (is (r/status-created? response))
          (is (= my-id-2 (get-in response [:headers "Location"])))

          ;; ensure that is can be retrieved (and flush index for elasticsearch)
          (let [retrieved-data (db/retrieve db my-id-2 nil)]
            (is (= my-data-2-with-acl retrieved-data)))

          ;; check that query has another entry
          (let [[query-meta query-hits] (db/query db collection-id {:nuvla/authn auth/internal-identity})]
            (is (= 2 (:count query-meta)))
            (is (= #{my-id my-id-2} (set (map :id query-hits)))))

<<<<<<< Updated upstream
          ;; adding the same entry again must fail
          (let [response (db/add db {:id my-id} nil)]
            (is (= 409 (:status response))))

          ;; update the entry
          (let [updated-data (assoc my-data-with-acl :two "3")
                response     (db/edit db updated-data nil)]
            (is (= 200 (:status response)))
=======
          (testing "adding the same entry again must fail"
            (is (r/status-conflict? (try-call #(db/add db {:id my-id} nil)))))

          (testing "update the entry"
            (let [updated-data (assoc my-data-with-acl :two "3")
                  response     (db/edit db updated-data nil)]
              (is (r/status-ok? response))
>>>>>>> Stashed changes

            ;; make sure that the update was applied
            (let [retrieved-data (db/retrieve db my-id nil)]
              (is (= updated-data retrieved-data)))

<<<<<<< Updated upstream
=======
          (testing "updating the entry by script (partial update)"
            (let [options  {:doc {:two "4"}}
                  response (db/scripted-edit db my-id options)]
              (is (r/status-ok? response))
>>>>>>> Stashed changes

            ;; delete the first entry
            (let [response (db/delete db updated-data nil)]
              (is (= 200 (:status response))))

<<<<<<< Updated upstream
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
                (is (= 404 (:status response)))))))))))
=======
          (testing "delete the first entry"
            (let [response (db/delete db {:id my-id} nil)]
              (is (r/status-ok? response))))

          (testing "updating a deleted entry by script"
            (let [options {:doc {:two "4"}}]
              (is (r/status-not-found? (try-call #(db/scripted-edit db my-id options))))))

          (testing "delete the second entry"
            (let [response (db/delete db {:id my-id-2} nil)]
              (is (r/status-ok? response))))

          (testing "deleting the first one a second time should give a 404"
            (is (r/status-not-found? (try-call #(db/delete db {:id my-id} nil)))))

          (testing "also retrieving it should do the same"
            (is (r/status-not-found? (try-call #(db/retrieve db my-id nil))))
            ))))))
>>>>>>> Stashed changes
