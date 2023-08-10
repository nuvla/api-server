(ns sixsq.nuvla.events.event-manager-test
  (:require [clojure.test :refer [is testing]]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.events.std-events :as std-events]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.events.protocol :as p]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.response :as response]
            [sixsq.nuvla.server.util.time :as time])
  (:import (clojure.lang ExceptionInfo)))


(defn- has-keys [m & ks]
  (doseq [k ks]
    (if (vector? k)
      (is (get-in m k))
      (is (get m k))))
  m)


(defn- check-event [{:keys [event-type category resource-type resource-href parent] :as expected} event]
  (when (contains? expected :event-type)
    (is (= event-type (:event-type event))))
  (when (contains? expected :category)
    (is (= category (:category event))))
  (when (contains? expected :resource-type)
    (is (= resource-type (-> event :resource :resource-type))))
  (when (contains? expected :resource-href)
    (is (= resource-href (-> event :resource :href))))
  (when (contains? expected :parent)
    (is (= parent (:parent event)))))


(defn create-test-request
  ([resource-type body]
   (create-test-request resource-type nil nil body))
  ([resource-type resource-uuid body]
   (create-test-request resource-type resource-uuid nil body))
  ([resource-type resource-uuid action body]
   {:params (cond-> {:resource-name resource-type}
                    resource-uuid (assoc :uuid resource-uuid)
                    action (assoc :action action))
    :body   body
    :nuvla/authn
    {:user-id      "user/alpha"
     :active-claim "user/alpha"
     :claims       #{"user/alpha" "group/nuvla-user" "group/nuvla-anon"}}}))


(defn is-status [response status]
  (is (= status (:status response)))
  response)


(defn add-and-retrieve-resource [resource-type data]
  (let [create-resource-request (create-test-request resource-type data)
        {:keys [status body]} (crud/add create-resource-request)]
    (is (= 201 status))
    (crud/retrieve-by-id-as-admin (:resource-id body))))


(defn add-resource-event
  [event-manager resource-id event]
  (let [request (create-test-request "event" event)
        {:keys [status body]} (p/add-resource-event event-manager request resource-id event)]
    (is (= 201 status))
    (crud/retrieve-by-id-as-admin (:resource-id body))))


(defn add-collection-event [event-manager resource-type event]
  (let [request (create-test-request "event" event)
        {:keys [status body]} (p/add-collection-event event-manager request resource-type event)]
    (is (= 201 status))
    (crud/retrieve-by-id-as-admin (:resource-id body))))


(defn check-add-event
  [event-manager]
  (let [resource-type "user"
        event         {:event-type "user.create"}
        resource-uuid (u/random-uuid)
        resource-id   (u/resource-id resource-type resource-uuid)]

    (testing "adding and searching collection events"
      (add-collection-event event-manager resource-type event)
      (let [search-results (p/search event-manager {:resource-type "user"})]
        (is (= 1 (count search-results)))
        (is (= event (select-keys (first search-results) (keys event))))))

    (testing "adding and searching resource events"
      (add-resource-event event-manager resource-id event)
      (let [search-results (p/search event-manager {:resource-href resource-id})]
        (is (= 1 (count search-results)))
        (is (= event (select-keys (first search-results) (keys event))))))

    (testing "acl"
      (testing "acl from resource"
        (let [valid-email {:address   "admin@example.com"
                           :validated true}
              email       (add-and-retrieve-resource "email" valid-email)
              event       (add-resource-event event-manager (:id email)
                                              {:event-type "email.create"
                                               :category   "command"})]
          (is (= (:acl event) (:acl email))))))

    (testing "parent field"
      (testing "valid parent event"
        (let [good-parent (add-resource-event event-manager resource-id event)
              event       (assoc event :parent (:id good-parent))]
          (add-resource-event event-manager resource-id event)))

      (testing "invalid parent event"
        (let [non-existing-parent-id (u/resource-id event/resource-type "xyz")
              event                  (assoc event :parent non-existing-parent-id)
              ex                     (is (thrown? ExceptionInfo
                                                  (add-resource-event event-manager resource-id event)))]
          (is (= 404 (:status (ex-data ex))))))

      (testing "invalid parent type"
        (let [valid-email {:address   "admin2@example.com"
                           :validated true}
              email       (add-and-retrieve-resource "email" valid-email)
              event       (assoc event :parent (:id email))
              ex          (is (thrown? ExceptionInfo
                                       (add-resource-event event-manager resource-id event)))]
          (is (= 400 (:status (ex-data ex)))))))))


(defn check-search-events
  [event-manager]
  (doseq [event-type ["user.create" "user.create.completed"]
          timestamp  ["2015-01-16T08:05:00.000Z" "2015-01-17T08:05:00.000Z" (time/now-str)]]
    (p/add-resource-event
      event-manager
      {:nuvla/authn auth/internal-identity}
      "user/1"
      {:event-type event-type
       :acl        {:owners ["group/nuvla-admin"]}
       :timestamp  timestamp}))
  (let [search (fn [resource-id opts]
                 (p/search event-manager (assoc opts :resource-href resource-id)))]
    (is (= 6 (count (search "user/1" {}))))
    (is (= 0 (count (search "user/2" {}))))
    (is (= 3 (count (search "user/1" {:category "command"}))))
    (is (= 3 (count (search "user/1" {:category "crud"}))))
    (is (= 3 (count (search "user/1" {:event-type "user.create"}))))
    (is (= 3 (count (search "user/1" {:event-type "user.create.completed"}))))
    (is (= 6 (count (search "user/1" {:start "2015-01-16T08:05:00.000Z"}))))
    (is (= 2 (count (search "user/1" {:end "2015-01-16T08:06:00.000Z"}))))
    (is (= 1 (count (search "user/1" {:category "command"
                                      :start    "now/d" :end "now+1d/d"}))))))


(defn check-crud-add-wrapper
  [event-manager]
  (testing "events on successful resource creation"
    (let [resource-type   "fake"
          resource-id     (u/resource-id resource-type (u/random-uuid))
          add-fn          (fn [_] (response/response-created resource-id))
          add-with-events (p/wrap-crud-add event-manager add-fn)
          request         (create-test-request resource-type {})
          response        (add-with-events request)]
      (is (= 201 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake.create"
                      :resource-type "fake"
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake.create.completed"
                      :resource-type "fake"
                      :resource-href resource-id
                      :parent        (:id event1)}
                     event2))))
  (testing "events on creation attempt with conflict failure"
    (let [resource-type   "fake2"
          resource-id     (u/resource-id resource-type (u/random-uuid))
          add-fn          (fn [_] (response/response-conflict resource-id))
          add-with-events (p/wrap-crud-add event-manager add-fn)
          request         (create-test-request resource-type {})
          response        (add-with-events request)]
      (is (= 409 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake2.create"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake2.create.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2))))
  (testing "events on creation attempt with unexpected failure"
    (let [resource-type   "fake3"
          add-fn          (fn [_] (throw (ex-info "unexpected error" {})))
          add-with-events (p/wrap-crud-add event-manager add-fn)
          request         (create-test-request resource-type {})]
      (is (thrown? ExceptionInfo (add-with-events request)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake3.create"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake3.create.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2)))))

(defn check-crud-edit-wrapper [event-manager]
  (testing "events on successful resource update"
    (let [resource-type    "fake"
          resource-uuid    (u/random-uuid)
          resource-id      (u/resource-id resource-type resource-uuid)
          edit-fn          (fn [_] (response/response-updated resource-id))
          edit-with-events (p/wrap-crud-edit event-manager edit-fn)
          request          (create-test-request resource-type resource-uuid {})
          response         (edit-with-events request)]
      (is (= 200 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake.update"
                      :resource-type "fake"
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake.update.completed"
                      :resource-type "fake"
                      :resource-href resource-id
                      :parent        (:id event1)}
                     event2))))
  (testing "events on failed update attempt due to resource not found error"
    (let [resource-type    "fake2"
          resource-uuid    (u/random-uuid)
          resource-id      (u/resource-id resource-type resource-uuid)
          edit-fn          (fn [_] (response/response-not-found resource-id))
          edit-with-events (p/wrap-crud-edit event-manager edit-fn)
          request          (create-test-request resource-type resource-uuid {})
          response         (edit-with-events request)]
      (is (= 404 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake2.update"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake2.update.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2))))
  (testing "events on update attempt with unexpected failure"
    (let [resource-type    "fake3"
          resource-uuid    (u/random-uuid)
          edit-fn          (fn [_] (throw (ex-info "unexpected error" {})))
          edit-with-events (p/wrap-crud-edit event-manager edit-fn)
          request          (create-test-request resource-type resource-uuid {})]
      (is (thrown? ExceptionInfo (edit-with-events request)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake3.update"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake3.update.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2)))))

(defn check-crud-delete-wrapper [event-manager]
  (testing "events on successful resource deletion"
    (let [resource-type      "fake"
          resource-uuid      (u/random-uuid)
          resource-id        (u/resource-id resource-type resource-uuid)
          delete-fn          (fn [_] (response/response-deleted resource-id))
          delete-with-events (p/wrap-crud-delete event-manager delete-fn)
          request            (create-test-request resource-type resource-uuid {})
          response           (delete-with-events request)]
      (is (= 200 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake.delete"
                      :resource-type "fake"
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake.delete.completed"
                      :resource-type "fake"
                      :resource-href resource-id
                      :parent        (:id event1)}
                     event2))))
  (testing "events on failed delete attempt due to resource not found error"
    (let [resource-type      "fake2"
          resource-uuid      (u/random-uuid)
          resource-id        (u/resource-id resource-type resource-uuid)
          delete-fn          (fn [_] (response/response-not-found resource-id))
          delete-with-events (p/wrap-crud-delete event-manager delete-fn)
          request            (create-test-request resource-type resource-uuid {})
          response           (delete-with-events request)]
      (is (= 404 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake2.delete"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake2.delete.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2))))
  (testing "events on delete attempt with unexpected failure"
    (let [resource-type      "fake3"
          resource-uuid      (u/random-uuid)
          delete-fn          (fn [_] (throw (ex-info "unexpected error" {})))
          delete-with-events (p/wrap-crud-delete event-manager delete-fn)
          request            (create-test-request resource-type resource-uuid {})]
      (is (thrown? ExceptionInfo (delete-with-events request)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake3.delete"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake3.delete.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2)))))

(defn check-action-wrapper [event-manager]
  (testing "events on successful action execution"
    (let [resource-type         "fake"
          resource-uuid         (u/random-uuid)
          resource-id           (u/resource-id resource-type resource-uuid)
          action                "action-xyz"
          do-action-fn          (fn [_] (response/map-response "action executed" 202))
          do-action-with-events (p/wrap-action event-manager do-action-fn)
          request               (create-test-request resource-type resource-uuid action {})
          response              (with-redefs [std-events/supported-event-types-impl
                                              (constantly (std-events/action-event-types resource-type action))]
                                  (do-action-with-events request))]
      (is (= 202 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake.action-xyz"
                      :resource-type "fake"
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake.action-xyz.completed"
                      :resource-type "fake"
                      :resource-href resource-id
                      :parent        (:id event1)}
                     event2))))
  (testing "events on failed action execution due to resource not found error"
    (let [resource-type         "fake2"
          resource-uuid         (u/random-uuid)
          resource-id           (u/resource-id resource-type resource-uuid)
          action                "action-xyz"
          do-action-fn          (fn [_] (response/response-not-found resource-id))
          do-action-with-events (p/wrap-action event-manager do-action-fn)
          request               (create-test-request resource-type resource-uuid action {})
          response              (with-redefs [std-events/supported-event-types-impl
                                              (constantly (std-events/action-event-types resource-type action))]
                                  (do-action-with-events request))]
      (is (= 404 (:status response)))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake2.action-xyz"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake2.action-xyz.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2))))
  (testing "events on action execution with unexpected failure"
    (let [resource-type         "fake3"
          resource-uuid         (u/random-uuid)
          action                "action-xyz"
          do-action-fn          (fn [_] (throw (ex-info "unexpected error" {})))
          do-action-with-events (p/wrap-action event-manager do-action-fn)
          request               (create-test-request resource-type resource-uuid action {})]
      (is (thrown? ExceptionInfo (with-redefs [std-events/supported-event-types-impl
                                               (constantly (std-events/action-event-types resource-type action))]
                                   (do-action-with-events request))))
      (let [[event1 event2 :as search-results] (p/search event-manager {:resource-type resource-type})]
        (is (= 2 (count search-results)))
        (check-event {:event-type    "fake3.action-xyz"
                      :resource-type resource-type
                      :parent        nil}
                     event1)
        (check-event {:event-type    "fake3.action-xyz.failed"
                      :resource-type resource-type
                      :parent        (:id event1)}
                     event2)))))


(defn check-disable-events
  [event-manager]
  (with-redefs [std-events/events-enabled?-impl (constantly false)]
    (testing "events on successful resource creation"
      (let [resource-type   "fake"
            resource-id     (u/resource-id resource-type (u/random-uuid))
            add-fn          (fn [_] (response/response-created resource-id))
            add-with-events (p/wrap-crud-add event-manager add-fn)
            request         (create-test-request resource-type {})
            response        (add-with-events request)]
        (is (= 201 (:status response)))
        (let [search-results (p/search event-manager {:resource-type resource-type})]
          (is (empty? search-results)))))
    (testing "events on successful resource update"
      (let [resource-type    "fake"
            resource-uuid    (u/random-uuid)
            resource-id      (u/resource-id resource-type resource-uuid)
            edit-fn          (fn [_] (response/response-updated resource-id))
            edit-with-events (p/wrap-crud-edit event-manager edit-fn)
            request          (create-test-request resource-type resource-uuid {})
            response         (edit-with-events request)]
        (is (= 200 (:status response)))
        (let [search-results (p/search event-manager {:resource-type resource-type})]
          (is (empty? search-results)))))
    (testing "events on successful resource deletion"
      (let [resource-type      "fake"
            resource-uuid      (u/random-uuid)
            resource-id        (u/resource-id resource-type resource-uuid)
            delete-fn          (fn [_] (response/response-deleted resource-id))
            delete-with-events (p/wrap-crud-delete event-manager delete-fn)
            request            (create-test-request resource-type resource-uuid {})
            response           (delete-with-events request)]
        (is (= 200 (:status response)))
        (let [search-results (p/search event-manager {:resource-type resource-type})]
          (is (empty? search-results)))))
    (testing "events on successful action execution"
      (let [resource-type         "fake"
            resource-uuid         (u/random-uuid)
            action                "action-xyz"
            do-action-fn          (fn [_] (response/map-response "action executed" 202))
            do-action-with-events (p/wrap-action event-manager do-action-fn)
            request               (create-test-request resource-type resource-uuid action {})
            response              (with-redefs [std-events/supported-event-types-impl
                                                (constantly (std-events/action-event-types resource-type action))]
                                    (do-action-with-events request))]
        (is (= 202 (:status response)))
        (let [search-results (p/search event-manager {:resource-type resource-type})]
          (is (empty? search-results)))))))
