(ns sixsq.nuvla.auth.acl-resource-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.auth.acl-resource :as acl]
    [clojure.tools.logging :as log]))


(deftest check-current-authentication
  (are [expect arg] (= expect (:identity (acl/current-authentication arg)))
                    nil {}
                    nil {:identity {:authentications {}}}
                    nil {:identity {:authentications {"user/user1" {:identity "user/user1"}}}}
                    nil {:identity {:current         "user/other"
                                    :authentications {"user/user1" {:identity "user/user1"}}}}
                    "user/user1" {:identity {:current         "user/user1"
                                             :authentications {"user/user1" {:identity "user/user1"}}}}))


(deftest check-extract-right

  (is (= ::acl/edit-acl (acl/extract-right {:identity "user/anyone" :roles ["group/r1", "group/nuvla-admin"]}
                                           [:edit-acl ["user/user1"]])))

  (is (= ::acl/view-acl (acl/extract-right nil [:view-acl ["group/nuvla-anon"]])))
  (is (= ::acl/view-acl (acl/extract-right {} [:view-acl ["group/nuvla-anon"]])))

  (is (nil? (acl/extract-right {:identity "user/unknown" :roles ["group/nuvla-anon"]}
                               [:edit-acl ["group/nuvla-user"]])))

  (let [id-map {:identity "user/user1" :roles ["group/r1" "group/r3"]}]
    (are [expect arg] (= expect (acl/extract-right id-map arg))
                      ::acl/edit-acl [:edit-acl ["user/user1"]]
                      nil [nil ["user/user1"]]
                      nil [:edit-acl ["group/user1"]]
                      ::acl/view-acl [:view-acl ["group/r1"]]
                      nil [:view-acl ["user/r1"]]
                      nil [:edit-acl ["group/r2"]]
                      ::acl/edit-acl [:edit-acl ["group/r3"]]
                      nil [nil ["group/r3"]]
                      nil [nil ["group/nuvla-anon"]]
                      ::acl/view-acl [:view-acl ["group/nuvla-anon"]]
                      nil nil
                      nil []))

  (let [acl {:owners   ["user/user1"]
             :view-acl ["group/role1"]
             :edit-acl ["user/user2"]
             :manage   ["group/role2"]}]

    (are [expect arg] (= expect (acl/extract-rights arg acl))
                      #{} nil
                      #{} {:identity nil}
                      #{::acl/edit-acl} {:identity "user/user1"}
                      #{::acl/edit-acl ::acl/view-acl} {:identity "user/user1" :roles ["group/role1"]}
                      #{} {:identity "user/unknown" :roles ["group/unknown"]}
                      #{::acl/view-acl} {:identity "user/unknown" :roles ["group/role1"]}
                      #{::acl/edit-acl ::acl/manage} {:identity "user/user1" :roles ["group/role2"]}
                      #{::acl/view-acl ::acl/edit-acl} {:identity "user/user2" :roles ["group/role1"]})))


(deftest check-hierarchy

  (are [parent child] (and (isa? acl/rights-hierarchy parent child)
                           (not (isa? acl/rights-hierarchy child parent)))

                      ::acl/view-acl ::acl/view-data
                      ::acl/view-acl ::acl/view-meta
                      ::acl/view-data ::acl/view-meta

                      ::acl/edit-acl ::acl/edit-data
                      ::acl/edit-acl ::acl/edit-meta
                      ::acl/edit-acl ::acl/manage
                      ::acl/edit-acl ::acl/delete
                      ::acl/edit-data ::acl/edit-meta

                      ::acl/edit-acl ::acl/view-acl
                      ::acl/edit-acl ::acl/view-data
                      ::acl/edit-acl ::acl/view-meta

                      ::acl/edit-data ::acl/view-data
                      ::acl/edit-data ::acl/view-meta

                      ::acl/edit-meta ::acl/view-meta)

  (let [independent-rights #{::acl/manage ::acl/delete ::acl/view-acl}]
    (doseq [right1 independent-rights
            right2 independent-rights]
      (if (= right1 right2)
        (is (isa? acl/rights-hierarchy right1 right2))
        (is (not (isa? acl/rights-hierarchy right1 right2))))))

  ;; everything is an instance of itself
  (doseq [right acl/rights-keywords]
    (is (isa? acl/rights-hierarchy right right)))

  ;; ::edit-acl covers everything, nothing covers ::edit-acl
  (let [all-rights (set (vals acl/rights-keywords))]
    ;; everything is an instance of itself
    (doseq [right all-rights]
      (is (isa? acl/rights-hierarchy right right)))

    ;; ::all covers everything, nothing covers ::edit-acl
    (doseq [right (disj all-rights ::acl/edit-acl)]
      (is (isa? acl/rights-hierarchy ::acl/edit-acl right))
      (is (not (isa? acl/rights-hierarchy right ::acl/edit-acl)))))

  (doseq [right (vals (dissoc acl/rights-keywords ::acl/edit-acl :edit-acl))]
    (is (isa? acl/rights-hierarchy ::acl/edit-acl right))
    (is (not (isa? acl/rights-hierarchy right ::acl/edit-acl)))))


(deftest check-can-do?
  (let [acl {:owners   ["user/user1"]
             :view-acl ["group/role1"]
             :edit-acl ["user/user2"]}
          resource {:acl         acl
                    :resource-id "Resource/uuid"}]

    (let [request {:identity {:current         "user/user1"
                              :authentications {"user/user1" {:identity "user/user1"}}}}]
      (is (= resource (acl/can-do? resource request ::acl/view-acl)))
      (is (= resource (acl/can-do? resource request ::acl/edit-acl)))
      (is (= resource (acl/can-do? resource request ::acl/edit-data)))
      (is (= resource (acl/can-do? resource request ::acl/view-data)))
      (is (= resource (acl/can-do? resource request ::acl/edit-meta)))
      (is (= resource (acl/can-do? resource request ::acl/view-meta)))
      (is (= resource (acl/can-do? resource request ::acl/delete)))
      (is (= resource (acl/can-do? resource request ::acl/manage)))

      (let [request {:identity {:current         "user/unknown"
                                :authentications {"user/unknown" {:identity "user/unknown"
                                                                  :roles    ["group/role1"]}}}}]
        (is (thrown? Exception (acl/can-do? resource request ::acl/edit-acl)))
        (is (= resource (acl/can-do? resource request ::acl/view-acl))))

      (let [request {:identity {:current         "user/unknown"
                                :authentications {"user/unknown" {:identity "user/unknown"
                                                                  :roles    ["group/unknown"]}}}}]
        (is (thrown? Exception (acl/can-do? resource request ::acl/edit-acl)))
        (is (thrown? Exception (acl/can-do? resource request ::acl/view-acl)))
        (is (thrown? Exception (acl/can-do? resource request ::acl/manage))))

      (let [request {:identity {:current         "user/user2"
                                :authentications {"user/user2" {:identity "user/user2"}}}}]
        (is (= resource (acl/can-do? resource request ::acl/edit-acl))))

      (let [request {:identity {:current         "user/user2"
                                :authentications {"user/user2" {:identity "user/user2"
                                                                :roles    ["group/role1"]}}}}]
        (is (= resource (acl/can-do? resource request ::acl/edit-acl)))
        (is (= resource (acl/can-do? resource request ::acl/view-acl)))))))
