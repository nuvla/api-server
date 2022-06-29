(ns sixsq.nuvla.server.resources.session-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.resources.session :as t]))

(deftest build-group-hierarchy
  (let [group-a  {:name "Group a" :description "Group a description" :id "group/a"}
        group-b  {:id "group/b" :parents ["group/a"]}
        group-c  {:id "group/c" :parents ["group/a" "group/b"]}
        group-z  {:id "group/z"}
        group-b1 {:id "group/b1" :parents ["group/a"]}]
    (are [expect arg] (= expect (t/build-group-hierarchy arg))
                      [] {:groups     []
                          :subgroups  []
                          :groups-ids #{}}
                      [{:description "Group a description"
                        :id          "group/a"
                        :name        "Group a"}] {:root-groups  [group-a]
                                                  :subgroups    []
                                                  :all-grps-ids #{"group/a"}}
                      [{:children    [{:id "group/b"}]
                        :description "Group a description"
                        :id          "group/a"
                        :name        "Group a"}] {:root-groups  [group-a]
                                                  :subgroups    [group-b]
                                                  :all-grps-ids #{"group/a"
                                                                  "group/b"}}
                      [{:id "group/z"}
                       {:children    [{:children [{:id "group/c"}]
                                       :id       "group/b"}
                                      {:id "group/b1"}]
                        :description "Group a description"
                        :id          "group/a"
                        :name        "Group a"}] {:root-groups  [group-b
                                                                 group-a
                                                                 group-z]
                                                  :subgroups    [group-b
                                                                 group-c
                                                                 group-b1]
                                                  :all-grps-ids #{"group/c"
                                                                  "group/b"
                                                                  "group/b1"
                                                                  "group/a"
                                                                  "group/z"}})))
