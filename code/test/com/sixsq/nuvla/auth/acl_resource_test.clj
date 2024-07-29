(ns com.sixsq.nuvla.auth.acl-resource-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]))


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

  (let [acl (acl-utils/normalize-acl {:owners   ["user/user1"]
                                      :view-acl ["group/role1"]
                                      :edit-acl ["user/user2"]
                                      :manage   ["group/role2"]})]


    (are [expect authn-info] (= expect (a/extract-rights authn-info acl))
                             #{} nil

                             #{} {}

                             #{::a/edit-acl
                               ::a/edit-data
                               ::a/delete
                               ::a/view-acl
                               ::a/view-meta
                               ::a/manage
                               ::a/edit-meta
                               ::a/view-data
                               ::a/query
                               ::a/bulk-delete
                               ::a/bulk-action
                               ::a/add} {:claims #{"user/user1"}}

                             #{::a/edit-acl
                               ::a/edit-data
                               ::a/delete
                               ::a/view-acl
                               ::a/view-meta
                               ::a/manage
                               ::a/edit-meta
                               ::a/view-data
                               ::a/query
                               ::a/bulk-delete
                               ::a/bulk-action
                               ::a/add} {:claims #{"group/nuvla-admin"}}

                             #{::a/view-acl
                               ::a/view-data
                               ::a/view-meta} {:claims #{"group/role1"}}
                             #{} {:claims #{"user/unknown", "group/unknown"}}

                             #{::a/view-acl
                               ::a/view-data
                               ::a/view-meta} {:claims #{"user/unknown", "group/role1"}}

                             #{::a/edit-acl
                               ::a/edit-data
                               ::a/delete
                               ::a/view-acl
                               ::a/view-meta
                               ::a/manage
                               ::a/edit-meta
                               ::a/view-data
                               ::a/query
                               ::a/bulk-delete
                               ::a/bulk-action
                               ::a/add} {:claims #{"user/user1", "group/role2"}}

                             #{::a/delete
                               ::a/edit-acl
                               ::a/edit-data
                               ::a/edit-meta
                               ::a/manage
                               ::a/view-acl
                               ::a/view-data
                               ::a/view-meta} {:claims #{"user/user2", "group/role1"}}

                             ))

  (is (= #{::a/query} (a/extract-rights {:claims #{"group/nuvla-anon"}} {:owners ["group/nuvla-admin"]
                                                                         :query  ["group/nuvla-anon"]})))

  )


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

                      ::a/edit-meta ::a/view-meta

                      ::a/delete ::a/view-meta)

  (let [independent-rights #{::a/manage ::a/delete ::a/view-acl ::a/query ::a/add}]
    (doseq [right1 independent-rights
            right2 independent-rights]
      (if (= right1 right2)
        (is (isa? a/rights-hierarchy right1 right2))
        (is (not (isa? a/rights-hierarchy right1 right2))))))

  ;; everything is an instance of itself
  (doseq [right a/rights-keywords]
    (is (isa? a/rights-hierarchy right right)))

  (let [all-rights (disj (set (vals a/rights-keywords)) ::a/query ::a/add)]

    ;; ::edit-acl covers everything except ::query and ::add, nothing covers ::edit-acl
    (doseq [right all-rights]
      (is (isa? a/rights-hierarchy right right)))

    ;; ::edit-acl covers everything except ::query and ::add
    (doseq [right (disj all-rights
                        ::a/edit-acl
                        ::a/query
                        ::a/add
                        ::a/bulk-delete
                        ::a/bulk-action)]
      (is (isa? a/rights-hierarchy ::a/edit-acl right))
      (is (not (isa? a/rights-hierarchy right ::a/edit-acl)))))

  (doseq [right (vals (dissoc a/rights-keywords
                              ::a/edit-acl :edit-acl
                              ::a/query :query
                              ::a/add :add
                              ::a/bulk-delete :bulk-delete
                              ::a/bulk-action :bulk-action))]
    (is (isa? a/rights-hierarchy ::a/edit-acl right))
    (is (not (isa? a/rights-hierarchy right ::a/edit-acl)))))


(deftest acl-append
  (let [acl {:owners ["user/a"]
             :manage ["user/b"]}]
    (are [expect right-kw user-id]
      (= expect (a/acl-append acl right-kw user-id))
      acl :edit-data nil
      acl :manage "user/b"
      {:owners    ["user/a"]
       :edit-data ["user/b"]
       :manage    ["user/b"]} :edit-data "user/b")))


(deftest acl-remove
  (let [acl {:owners    ["user/a"]
             :edit-data ["user/b"]
             :manage    ["user/b"]}]
    (are [expect user-id]
      (= expect (a/acl-remove acl user-id))
      acl nil
      {:owners    ["user/a"]} "user/b")))

(deftest is-admin-request?
  (is (true? (a/is-admin-request? {:nuvla/authn {:claims ["group/nuvla-admin"]}})))
  (is (false? (a/is-admin-request? {:nuvla/authn {:claims ["group/nuvla-user"]}}))))
