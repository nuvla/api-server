(ns sixsq.nuvla.auth.acl-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.auth.acl_resource :as a]))


(deftest check-current-authentication
  (are [expect arg] (= expect (:identity (a/current-authentication arg)))
                    nil {}
                    nil {:identity {:authentications {}}}
                    nil {:identity {:authentications {"user/user1" {:identity "user/user1"}}}}
                    nil {:identity {:current         "user/other"
                                    :authentications {"user/user1" {:identity "user/user1"}}}}
                    "user/user1" {:identity {:current         "user/user1"
                                             :authentications {"user/user1" {:identity "user/user1"}}}}))


(deftest check-extract-right

  (is (= ::a/edit-acl (a/extract-right {:identity "user/anyone" :roles ["group/r1", "group/nuvla-admin"]}
                                       [:edit-acl ["user/user1"]])))

  (is (= ::a/view-acl (a/extract-right nil [:view-acl ["group/nuvla-anon"]])))
  (is (= ::a/view-acl (a/extract-right {} [:view-acl ["group/nuvla-anon"]])))

  (is (nil? (a/extract-right {:identity "user/unknown" :roles ["group/nuvla-anon"]}
                             [:edit-acl ["group/nuvla-user"]])))

  (let [id-map {:identity "user/user1" :roles ["group/r1" "group/r3"]}]
    (are [expect arg] (= expect (a/extract-right id-map arg))
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

    (are [expect arg] (= expect (a/extract-rights arg acl))
                      #{} nil
                      #{} {:identity nil}
                      #{::a/edit-acl} {:identity "user/user1"}
                      #{::a/edit-acl ::a/view-acl} {:identity "user/user1" :roles ["group/role1"]}
                      #{} {:identity "user/unknown" :roles ["group/unknown"]}
                      #{::a/view-acl} {:identity "user/unknown" :roles ["group/role1"]}
                      #{::a/edit-acl ::a/manage} {:identity "user/user1" :roles ["group/role2"]}
                      #{::a/view-acl ::a/edit-acl} {:identity "user/user2" :roles ["group/role1"]})))


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

    (let [request {:identity {:current         "user/user1"
                              :authentications {"user/user1" {:identity "user/user1"}}}}]
      (is (= resource (a/can-do? resource request ::a/view-acl)))
      (is (= resource (a/can-do? resource request ::a/edit-acl)))
      (is (= resource (a/can-do? resource request ::a/edit-data)))
      (is (= resource (a/can-do? resource request ::a/view-data)))
      (is (= resource (a/can-do? resource request ::a/edit-meta)))
      (is (= resource (a/can-do? resource request ::a/view-meta)))
      (is (= resource (a/can-do? resource request ::a/delete)))
      (is (= resource (a/can-do? resource request ::a/manage)))

      (let [request {:identity {:current         "user/unknown"
                                :authentications {"user/unknown" {:identity "user/unknown"
                                                                  :roles    ["group/role1"]}}}}]
        (is (thrown? Exception (a/can-do? resource request ::a/edit-acl)))
        (is (= resource (a/can-do? resource request ::a/view-acl))))

      (let [request {:identity {:current         "user/unknown"
                                :authentications {"user/unknown" {:identity "user/unknown"
                                                                  :roles    ["group/unknown"]}}}}]
        (is (thrown? Exception (a/can-do? resource request ::a/edit-acl)))
        (is (thrown? Exception (a/can-do? resource request ::a/view-acl)))
        (is (thrown? Exception (a/can-do? resource request ::a/manage))))

      (let [request {:identity {:current         "user/user2"
                                :authentications {"user/user2" {:identity "user/user2"}}}}]
        (is (= resource (a/can-do? resource request ::a/edit-acl))))

      (let [request {:identity {:current         "user/user2"
                                :authentications {"user/user2" {:identity "user/user2"
                                                                :roles    ["group/role1"]}}}}]
        (is (= resource (a/can-do? resource request ::a/edit-acl)))
        (is (= resource (a/can-do? resource request ::a/view-acl)))))))
