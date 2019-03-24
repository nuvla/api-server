(ns sixsq.nuvla.auth.acl-resource-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.auth.acl-resource :as acl]))


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
  (doseq [right (disj acl/rights-keywords ::acl/edit-acl)]
    (is (isa? acl/rights-hierarchy ::acl/edit-acl right))
    (is (not (isa? acl/rights-hierarchy right ::acl/edit-acl)))))
