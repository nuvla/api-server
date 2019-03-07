(ns sixsq.nuvla.db.binding-queries
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.db.binding :as db]
    [sixsq.nuvla.db.filter.parser :as parser]))

(s/def ::id string?)
(s/def ::sequence int?)
(s/def ::attr1 string?)
(s/def ::attr2 string?)
(s/def ::admin boolean?)
(s/def ::user boolean?)

(s/def ::type string?)
(s/def ::principal string?)
(s/def ::right string?)

(s/def ::owner (s/keys :req-un [::type ::principal]))
(s/def ::rule (s/keys :req-un [::type ::principal ::right]))
(s/def ::rules (s/coll-of ::rule :min-count 1 :kind vector?))

(s/def ::acl (s/keys :req-un [::owner]
                     :opt-un [::rules]))

(s/def ::resource (s/keys :req-un [::id ::sequence ::attr1 ::attr2 ::acl]
                          :opt-un [::admin ::user]))

(def admin-acl {:owner {:type "ROLE", :principal "ADMIN"}
                :rules [{:type "ROLE", :principal "ADMIN", :right "ALL"}]})

(def admin-role {:user-name "ADMIN", :user-roles ["ADMIN"]})

(def username "jane")

(def user-acl {:owner {:type "ROLE", :principal "ADMIN"}
               :rules [{:type "ROLE", :principal "ADMIN", :right "ALL"}
                       {:type "USER", :principal username, :right "ALL"}]})

(def user-role {:user-name username, :user-roles ["USER"]})


(defn check-binding-queries [db-impl]
  (with-open [db db-impl]

    (let [collection-id "test-collection"]

      ;; initialize the database
      (db/initialize db collection-id {:spec ::resource})

      ;; create an entry in the database
      (let [n 2
            collection-id "test-collection"
            admin-docs (doall (for [uuid (range 0 n)]
                                {:id       (str collection-id "/" uuid)
                                 :sequence uuid
                                 :attr1    "attr1"
                                 :attr2    "attr2"
                                 :number   1
                                 :nested   {:child "child1"}
                                 :admin    true
                                 :acl      admin-acl}))
            user-docs (doall (for [uuid (range n (* 2 n))]
                               {:id       (str collection-id "/" uuid)
                                :sequence uuid
                                :attr1    "attr1"
                                :attr2    "attr2"
                                :nested   {:child "child2"}
                                :user     true
                                :number   2
                                :acl      user-acl}))
            docs (vec (concat admin-docs user-docs))]

        ;; check schemas
        (doseq [doc docs]
          (is (s/valid? ::resource doc)))

        ;; add all of the docs to the database
        (doseq [doc docs]
          (let [doc-id (:id doc)
                response (db/add db doc nil)]
            (is (= 201 (:status response)))
            (is (= doc-id (get-in response [:headers "Location"])))))

        ;; ensure that all of them can be retrieved individually
        (doseq [doc docs]
          (let [doc-id (:id doc)
                retrieved-data (db/retrieve db doc-id nil)]
            (is (= doc retrieved-data))))

        ;; check that a query with an admin role retrieves everything
        (let [[query-meta query-hits] (db/query db collection-id admin-role)]
          (is (= (* 2 n) (:count query-meta)))
          (is (= (set docs) (set query-hits))))

        ;; check ascending ordering of the entries
        (let [options (merge admin-role
                             {:cimi-params {:orderby [["sequence" :asc]]}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= (* 2 n) (:count query-meta)))
          (is (= docs (vec query-hits))))

        ;; check descending ordering of the entries
        (let [options (merge admin-role
                             {:cimi-params {:orderby [["sequence" :desc]]}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= (* 2 n) (:count query-meta)))
          (is (= (reverse docs) (vec query-hits))))

        ;; check paging
        (let [n-drop (int (/ n 10))
              options (merge admin-role
                             {:cimi-params {:first   (inc n-drop)
                                            :last    (+ n n-drop)
                                            :orderby [["sequence" :desc]]}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= (* 2 n) (:count query-meta)))
          (is (= n (count query-hits)))
          (is (= (vec (take n (drop n-drop (reverse docs)))) (vec query-hits))))

        ;; check selection of attributes
        (let [options (merge admin-role
                             {:cimi-params {:select ["attr1" "sequence"]}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= (* 2 n) (:count query-meta)))
          (is (every? :attr1 query-hits))
          (is (every? :sequence query-hits))
          (is (every? :acl query-hits))                     ;; always added to select list
          (is (every? #(nil? (:id %)) query-hits))
          (is (every? #(nil? (:attr2 %)) query-hits)))

        ;; attribute exists
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter "admin!=null")}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set admin-docs) (set query-hits))))

        ;; attribute missing
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter "admin=null")}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set user-docs) (set query-hits))))

        ;; eq comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence=" n))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= 1 (:count query-meta)))
          (is (= (first user-docs) (first query-hits))))

        ;; ne comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence!=" n))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= (dec (* 2 n)) (:count query-meta)))
          (is (= (set (concat admin-docs (drop 1 user-docs))) (set query-hits))))

        ;; gte comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence>=" n))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set user-docs) (set query-hits))))

        ;; gt comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence>" (dec n)))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set user-docs) (set query-hits))))

        ;; lt comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence<" n))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set admin-docs) (set query-hits))))

        ;; lte comparison
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence<=" (dec n)))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= n (:count query-meta)))
          (is (= (set admin-docs) (set query-hits))))

        ;; or
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "sequence=0 or sequence=" n))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= 2 (:count query-meta)))
          (is (= #{(first admin-docs) (first user-docs)} (set query-hits))))

        ;; and
        (let [options (merge admin-role
                             {:cimi-params {:filter (parser/parse-cimi-filter (str "(sequence=0 and admin!=null) or (sequence=" n " and admin=null)"))}})
              [query-meta query-hits] (db/query db collection-id options)]
          (is (= 2 (:count query-meta)))
          (is (= #{(first admin-docs) (first user-docs)} (set query-hits))))

        ;; check that a query with an user role retrieves only user docs
        (let [[query-meta query-hits] (db/query db collection-id user-role)]
          (is (= n (:count query-meta)))
          (is (= (set user-docs) (set query-hits))))

        ;; aggregation
        (let [[query-meta query-hits] (db/query db collection-id (merge admin-role
                                                                        {:cimi-params {:aggregation
                                                                                       [[:terms "attr1"]
                                                                                        [:terms "nested/child"]
                                                                                        [:min "number"]
                                                                                        [:max "number"]
                                                                                        [:sum "number"]
                                                                                        [:avg "number"]
                                                                                        [:count "id"]
                                                                                        [:cardinality "id"]]}}))]

          (is (= {:terms:nested/child {:doc_count_error_upper_bound 0,
                                       :sum_other_doc_count         0,
                                       :buckets                     [{:key "child1", :doc_count 2}
                                                                     {:key "child2", :doc_count 2}]},
                  :cardinality:id     {:value 4},
                  :terms:attr1        {:doc_count_error_upper_bound 0, :sum_other_doc_count 0,
                                       :buckets                     [{:key "attr1", :doc_count 4}]},
                  :avg:number         {:value 1.5},
                  :min:number         {:value 1.0},
                  :value_count:id     {:value 4},
                  :max:number         {:value 2.0},
                  :sum:number         {:value 6.0}} (:aggregations query-meta))))

        ;; delete all of the docs
        (doseq [doc docs]
          (let [response (db/delete db doc nil)]
            (is (= 200 (:status response)))))

        ;; ensure that all of the docs have been deleted
        (doseq [doc docs]
          (try
            (db/delete db doc nil)
            (is (nil? "delete of non-existent resource did not throw an exception"))
            (catch Exception e
              (let [response (ex-data e)]
                (is (= 404 (:status response)))))))))))
