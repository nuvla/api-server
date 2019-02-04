(ns com.sixsq.slipstream.ssclj.resources.quota.utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.db.filter.parser :as parser]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.quota :as quota]
    [com.sixsq.slipstream.ssclj.resources.quota.utils :as t]
    [com.sixsq.slipstream.ssclj.resources.virtual-machine :as vm]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase quota/resource-name)))

(def vm-base-uri (str p/service-context (u/de-camelcase vm/resource-name)))

(defn make-quota
  [user aggregation limit]
  (let [timestamp "1964-08-25T10:00:00.0Z"
        valid-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal user
                            :type      "USER"
                            :right     "VIEW"}]}]
    {:id          (str quota/resource-url "/test-quota")
     :name        (str user " quota")
     :description (str "a quota for " user)
     :resourceURI quota/resource-uri
     :created     timestamp
     :updated     timestamp
     :acl         valid-acl

     :resource    "VirtualMachine"
     :selection   "id!=null"
     :aggregation aggregation
     :limit       limit}))

(defn random-virtual-machine
  []
  (let [resource-type "virtual-machine"
        doc-id (str resource-type "/" (cu/random-uuid))
        instance-id (cu/random-uuid)
        cloud (rand-nth ["connector/cloud-1" "connector/cloud-2" "connector/cloud-3"])
        credential (rand-nth ["credential/cloud-1" "credential/cloud-2" "credential/cloud-3"])
        offer (rand-nth ["service-offer/offer-1" "service-offer/offer-2" "service-offer/offer-3"])
        user (rand-nth ["jane" "tarzan"])]
    {:id           doc-id
     :name         user                                     ;; used to keep track of user name
     :resourceURI  "http://sixsq.com/slipstream/1/VirtualMachine"
     :created      "2017-09-04T09:39:35.651Z"
     :updated      "2017-09-04T09:39:35.679Z"

     :credentials  [{:href credential}]
     :connector    {:href cloud}
     :serviceOffer {:href         offer
                    :resource:cpu 8}
     :instanceID   instance-id
     :state        "Running"
     :acl          {:owner {:type      "USER"
                            :principal "ADMIN"}
                    :rules [{:principal "ADMIN"
                             :right     "ALL"
                             :type      "USER"}
                            {:principal user
                             :right     "VIEW"
                             :type      "USER"}]}}))

(defn create-virtual-machine []
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))]

    (let [vm (random-virtual-machine)]
      (-> session-admin
          (request vm-base-uri
                   :request-method :post
                   :body (json/write-str vm))
          (ltu/body->edn)
          (ltu/is-status 201))
      vm)))

(defn create-virtual-machines [n-vm]
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))]

    (frequencies (map :name (repeatedly n-vm create-virtual-machine)))))

(def quota-jane (make-quota "jane" "count:id" 100))

(deftest check-get-cimi-params
  (let [selection "filter='ok'"
        aggregation "count:id"
        quota {:selection   selection
               :aggregation aggregation}]
    (is (= {:first       1
            :last        0
            :filter      (parser/parse-cimi-filter selection)
            :aggregation {:count ["id"]}}
           (t/get-cimi-params quota)))))

(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-jane (header session-anon authn-info-header "jane USER ANON")
        session-tarzan (header session-anon authn-info-header "tarzan USER ANON")
        session-other (header session-anon authn-info-header "other USER ANON")]

    ;; create some virtual machine documents to test quota queries
    (let [n-vm 300]

      (let [freq (create-virtual-machines 300)]

        (-> session-admin
            (request vm-base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri vm/collection-uri)
            (ltu/is-count #(= n-vm %))
            (ltu/entries quota/resource-tag))

        (let [n-jane (-> session-jane
                         (request vm-base-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-resource-uri vm/collection-uri)
                         :response
                         :body
                         :count)

              n-tarzan (-> session-tarzan
                           (request vm-base-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-resource-uri vm/collection-uri)
                           :response
                           :body
                           :count)

              n-other (-> session-other
                          (request vm-base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-resource-uri vm/collection-uri)
                          :response
                          :body
                          :count)]

          (is (= n-jane (get freq "jane")))
          (is (= n-tarzan (get freq "tarzan")))
          (is (zero? n-other))

          ;; create the quota
          (let [uri (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str quota-jane))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))
                abs-uri (str p/service-context (u/de-camelcase uri))]

            ;; user view: OK
            (-> session-jane
                (request abs-uri)
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; admin view: OK
            (-> session-admin
                (request abs-uri)
                (ltu/body->edn)
                (ltu/is-status 200))

            (is (= n-vm (t/quota-metric quota-jane {})))
            (let [request {:user-name "jane" :user-roles ["USER" "ANON"]}
                  n-jane (t/quota-metric-user quota-jane request)]
              (is (> n-vm n-jane 0))

              (let [{:keys [currentAll currentUser limit]} (t/collect quota-jane request)]
                (is (= n-vm currentAll))
                (is (= n-jane currentUser))
                (is (= 100 limit))))))))))
