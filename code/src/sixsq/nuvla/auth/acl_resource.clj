(ns sixsq.nuvla.auth.acl-resource
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.util.response :as ru]))

(def rights-hierarchy (-> (make-hierarchy)

                          (derive ::edit-acl ::edit-data)
                          (derive ::edit-acl ::delete)
                          (derive ::edit-acl ::manage)
                          (derive ::edit-acl ::view-acl)

                          (derive ::edit-data ::edit-meta)
                          (derive ::edit-data ::view-data)

                          (derive ::edit-meta ::view-meta)

                          (derive ::view-acl ::view-data)
                          (derive ::view-data ::view-meta)))


(def rights-keywords
  (set (cons ::edit-acl (ancestors rights-hierarchy ::edit-acl))))
