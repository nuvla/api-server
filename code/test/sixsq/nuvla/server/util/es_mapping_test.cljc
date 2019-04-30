(ns sixsq.nuvla.server.util.es-mapping-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [sixsq.nuvla.db.es.common.es-mapping :as t]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.common-operation :as common-operation]
    [sixsq.nuvla.server.resources.spec.core :as core]))


(deftest common-schema

  (are [spec expected] (= (t/transform spec) expected)
                       common/common-attrs {}
                       common/create-attrs {}
                       common/template-attrs {}

                       ::core/href {:type "keyword"}
                       ::core/resource-link {:type "object", :properties {"href" {:type "keyword"}}}
                       ::core/resource-links {:type "object", :properties {"href" {:type "keyword"}}}

                       ::common/acl {:properties {"delete"    {:type "keyword"}
                                                  "edit-acl"  {:type "keyword"}
                                                  "edit-data" {:type "keyword"}
                                                  "edit-meta" {:type "keyword"}
                                                  "manage"    {:type "keyword"}
                                                  "owners"    {:type "keyword"}
                                                  "view-acl"  {:type "keyword"}
                                                  "view-data" {:type "keyword"}
                                                  "view-meta" {:type "keyword"}}
                                     :type       "object"}

                       ::common/operations {:type "object", :enabled false}


                       ::common/tags {:type "keyword", :copy_to "fulltext"}

                       ::common/id {:type "keyword", :copy_to "fulltext"}
                       ::common/resource-type {:type "keyword"}
                       ::common/created {:type "date", :format "strict_date_optional_time||epoch_millis"}
                       ::common/updated {:type "date", :format "strict_date_optional_time||epoch_millis"}
                       ::common/name {:type "keyword", :copy_to "fulltext"}
                       ::common/description {:type "keyword", :copy_to "fulltext"}
                       ::common-operation/operation {:type "object", :properties {"href" {:type "keyword"},
                                                                                  "rel"  {:type "keyword"}}}))
