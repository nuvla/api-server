(ns sixsq.nuvla.server.resources.spec.module-component-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.connector :as t]  ;; FIXME: Change to module-version when available.
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id               (str t/resource-type "/connector-uuid")
              :resource-type    t/resource-type
              :created          timestamp
              :updated          timestamp
              :acl              valid-acl

              :parentModule     {:href "module/my-parent-module"}
              :cpu              2
              :ram              2048
              :disk             100
              :volatileDisk     500
              :ports            ["udp:4000:4000" "tcp::22" "tcp:15000-15005:5000-5005" "tcp::6500-6503"]
              :mounts           ["src=abc,dst=/var/tmp/abc,readonly" "type=bind,src=,dst=/var/tmp/abc"]

              :networkType      "public"

              :inputParameters  [{:parameter "iparam-1" :description "desc2" :value "100"}
                                 {:parameter "iparam-2" :description "desc2"}
                                 {:parameter "iparam-3"}]

              :outputParameters [{:parameter "iparam-1" :description "desc2" :value "100"}
                                 {:parameter "iparam-2" :description "desc2"}
                                 {:parameter "iparam-3"}]

              :targets          {:preinstall  "preinstall"
                                 :packages    ["emacs-nox" "vim"]
                                 :postinstall "postinstall"
                                 :deployment  "deployment"
                                 :reporting   "reporting"
                                 :onVmAdd     "onVmAdd"
                                 :onVmRemove  "onVmRemove"
                                 :prescale    "prescale"
                                 :postscale   "postscale"}
              :author           "someone"
              :commit           "wip"}]

    (stu/is-valid ::module-component/module-component root)
    (stu/is-invalid ::module-component/module-component (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :networkType :outputParameters :author :parentModule}]
      (stu/is-invalid ::module-component/module-component (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:cpu :ram :disk :volatileDisk :ports :mounts :targets :inputParameters :commit}]
      (stu/is-valid ::module-component/module-component (dissoc root k)))))
