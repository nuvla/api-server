(ns sixsq.nuvla.auth.acl-resource-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.auth.acl-resource :as a]))


(deftest check-extract-right

  (is (= ::a/edit-acl (a/extract-right {:claims #{"group/r1" "group/nuvla-admin" "user/anyone"}}
                                       [:edit-acl ["user/user1"]])))

  (is (nil? (a/extract-right {:claims #{"group/nuvla-anon"}}
                             [:edit-acl ["group/nuvla-user"]])))

  (let [authn-info {:claims #{"user/user1" "group/r1" "group/r3" "group/nuvla-anon"}}]
    (are [expect arg] (= expect (a/extract-right authn-info arg))
                      ::a/edit-acl [:edit-acl ["user/user1"]]
                      nil [nil ["user/user1"]]
                      nil [:edit-acl ["group/user1"]]
                      ::a/view-acl [:view-acl ["group/r1"]]
                      nil [:view-acl ["user/r1"]]
                      nil [:edit-acl ["group/r2"]]
                      ::a/edit-acl [:edit-acl ["group/r3"]]
                      nil [nil ["group/r3"]]
                      nil [nil ["group/nuvla-anon"]]
                      ::a/view-acl [:view-acl ["group/nuvla-anon"]]
                      nil nil
                      nil []))

  (let [acl {:owners   ["user/user1"]
             :view-acl ["group/role1"]
             :edit-acl ["user/user2"]
             :manage   ["group/role2"]}]

    (are [expect authn-info] (= expect (a/extract-rights authn-info acl))
                             #{} nil
                             #{} {}
                             #{::a/edit-acl} {:claims #{"user/user1"}}
                             #{::a/view-acl} {:claims #{"group/role1"}}
                             #{} {:claims #{"user/unknown", "group/unknown"}}
                             #{::a/view-acl} {:claims #{"user/unknown", "group/role1"}}
                             #{::a/edit-acl ::a/manage} {:claims #{"user/user1", "group/role2"}}
                             #{::a/view-acl ::a/edit-acl} {:claims #{"user/user2", "group/role1"}})))


(deftest check-hierarchy

  (are [parent child] (and (isa? a/rights-hierarchy parent child)
                           (not (isa? a/rights-hierarchy child parent)))

                      ::a/view-acl ::a/view-data
                      ::a/view-acl ::a/view-meta
                      ::a/view-data ::a/view-meta

                      ::a/edit-acl ::a/edit-data
                      ::a/edit-acl ::a/edit-meta
                      ::a/edit-acl ::a/manage
                      ::a/edit-acl ::a/delete
                      ::a/edit-data ::a/edit-meta

                      ::a/edit-acl ::a/view-acl
                      ::a/edit-acl ::a/view-data
                      ::a/edit-acl ::a/view-meta

                      ::a/edit-data ::a/view-data
                      ::a/edit-data ::a/view-meta

                      ::a/edit-meta ::a/view-meta)

  (let [independent-rights #{::a/manage ::a/delete ::a/view-acl}]
    (doseq [right1 independent-rights
            right2 independent-rights]
      (if (= right1 right2)
        (is (isa? a/rights-hierarchy right1 right2))
        (is (not (isa? a/rights-hierarchy right1 right2))))))

  ;; everything is an instance of itself
  (doseq [right a/rights-keywords]
    (is (isa? a/rights-hierarchy right right)))

  ;; ::edit-acl covers everything, nothing covers ::edit-acl
  (let [all-rights (set (vals a/rights-keywords))]
    ;; everything is an instance of itself
    (doseq [right all-rights]
      (is (isa? a/rights-hierarchy right right)))

    ;; ::all covers everything, nothing covers ::edit-acl
    (doseq [right (disj all-rights ::a/edit-acl)]
      (is (isa? a/rights-hierarchy ::a/edit-acl right))
      (is (not (isa? a/rights-hierarchy right ::a/edit-acl)))))

  (doseq [right (vals (dissoc a/rights-keywords ::a/edit-acl :edit-acl))]
    (is (isa? a/rights-hierarchy ::a/edit-acl right))
    (is (not (isa? a/rights-hierarchy right ::a/edit-acl)))))


(deftest check-can-do?
  (let [acl {:owners   ["user/user1"]
             :view-acl ["group/role1"]
             :edit-acl ["user/user2"]}
        resource {:acl         acl
                  :resource-id "Resource/uuid"}]

    (let [request {:nuvla/authn {:claims #{"user/user1"}}}]
      (is (= resource (a/can-do? resource request ::a/view-acl)))
      (is (= resource (a/can-do? resource request ::a/edit-acl)))
      (is (= resource (a/can-do? resource request ::a/edit-data)))
      (is (= resource (a/can-do? resource request ::a/view-data)))
      (is (= resource (a/can-do? resource request ::a/edit-meta)))
      (is (= resource (a/can-do? resource request ::a/view-meta)))
      (is (= resource (a/can-do? resource request ::a/delete)))
      (is (= resource (a/can-do? resource request ::a/manage)))

      (let [request {:nuvla/authn {:claims #{"user/unknown" "group/role1"}}}]
        (is (thrown? Exception (a/can-do? resource request ::a/edit-acl)))
        (is (= resource (a/can-do? resource request ::a/view-acl))))

      (let [request {:nuvla/authn {:claims #{"user/unknown" "group/unknown"}}}]
        (is (thrown? Exception (a/can-do? resource request ::a/edit-acl)))
        (is (thrown? Exception (a/can-do? resource request ::a/view-acl)))
        (is (thrown? Exception (a/can-do? resource request ::a/manage))))

      (let [request {:nuvla/authn {:claims #{"user/user2"}}}]
        (is (= resource (a/can-do? resource request ::a/edit-acl))))

      (let [request {:nuvla/authn {:claims #{"user/user2" "group/role1"}}}]
        (is (= resource (a/can-do? resource request ::a/edit-acl)))
        (is (= resource (a/can-do? resource request ::a/view-acl)))))))
