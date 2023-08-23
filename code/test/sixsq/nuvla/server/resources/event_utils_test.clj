(ns sixsq.nuvla.server.resources.event-utils-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.time :as time]))


(use-fixtures :each ltu/with-test-server-fixture)


(defn req
  [{:keys [nuvla-authn-info method body]}]
  {:request-method method
   :params         {:resource-name "resource"
                    :uuid          "12345"}
   :headers        {"nuvla-authn-info" nuvla-authn-info}
   :body           body})


;; TODO: test getters in sixsq.nuvla.server.resources.event.utils


(deftest build-event
  (with-redefs [time/now-str (constantly "2023-08-17T07:25:57.259Z")]
    (let [context {:category "add"
                   :params   {:resource-name "resource"}}
          request (req {:nuvla-authn-info "super super group/nuvla-admin"
                        :method           :post
                        :body             {:k "v"}})]
      (testing "success"
        (let [uuid  (u/random-uuid)
              id    (str "resource/" uuid)
              event (t/build-event context request {:status 201 :body {:resource-id id}})]
          (is (= {:name          "resource.add"
                  :category      "add"
                  :description   (str "An anonymous user added resource " id ".")
                  :content       {:resource           {:href id}
                                  :linked-identifiers []}
                  :authn-info    {}
                  :success       true
                  :severity      "medium"
                  :resource-type "event"
                  :acl           {:owners ["group/nuvla-admin"]}
                  :timestamp     "2023-08-17T07:25:57.259Z"}
                 event))))
      (testing "failure"
        (let [event (t/build-event context request {:status 400})]
          (is (= {:name          "resource.add"
                  :category      "add"
                  :description   "resource.add attempt failed."
                  :content       {:resource           {:href nil}
                                  :linked-identifiers []}
                  :authn-info    {}
                  :success       false
                  :severity      "medium"
                  :resource-type "event"
                  :acl           {:owners ["group/nuvla-admin"]}
                  :timestamp     "2023-08-17T07:25:57.259Z"}
                 event)))))))


(deftest add-event
  (let [context {:category "action"
                 :params   {:resource-name "resource"}}
        request (req {:nuvla-authn-info "super super group/nuvla-admin"
                      :method           :post
                      :body             {:k "v"}})
        event   (t/build-event context request {:status 200})]
    (let [{:keys [status]} (t/add-event event)]
      (is (= 201 status)))))


(deftest search-event
  (doseq [category  ["action" "add"]
          timestamp ["2015-01-16T08:05:00.000Z" "2015-01-17T08:05:00.000Z" (time/now-str)]]
    (t/create-event "user/1" "hello" {:owners ["group/nuvla-admin"]}
                    :category category
                    :timestamp timestamp))
  (is (= 6 (count (t/search-event "user/1" {}))))
  (is (= 0 (count (t/search-event "user/2" {}))))
  (is (= 3 (count (t/search-event "user/1" {:category "action"}))))
  (is (= 6 (count (t/search-event "user/1" {:start "2015-01-16T08:05:00.000Z"}))))
  (is (= 2 (count (t/search-event "user/1" {:end "2015-01-16T08:06:00.000Z"}))))
  (is (= 1 (count (t/search-event "user/1" {:category "action"
                                            :start    "now/d" :end "now+1d/d"})))))

