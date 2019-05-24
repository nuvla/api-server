(ns sixsq.nuvla.server.resources.notification-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.notification :refer :all]))


(def base-uri (str p/service-context resource-type))

(def message "message")
(def content-unique-id "content-hash")

(def valid-notification {:message           message
                         :category          "some-category"
                         :content-unique-id content-unique-id})

(use-fixtures :once ltu/with-test-server-fixture)

(deftest lifecycle
  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        user          "user/jane"
        session-user  (header session-anon authn-info-header (format "%s group/nuvla-user group/nuvla-anon" user))
        session-admin (header session-anon authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    ;; admin can query; adding resources is allowed
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; user can query; adding resources is not allowed
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; query: forbidden for anon
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; create: forbidden for anon and user
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-notification))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; create: NOK with bad schema
    (doseq [[k v] {:message "" :category "A-Category" :content-unique-id ""}]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-notification k v)))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"(?s).*resource does not satisfy defined schema.*")))

    ;; Lifecycle: create, find by unique id, get, defer, delete.
    ;; Admin creates the notification for a user.
    ;; User should be able to defer and delete the notification.
    (let [acl     {:delete [user] :manage [user] :view-data [user] :view-meta [user]}
          uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str (assoc valid-notification :acl acl)))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; find by :content-unique-id
      (is (= message (-> session-admin
                         (content-type "application/x-www-form-urlencoded")
                         (request base-uri
                                  :request-method :put
                                  :body (rc/form-encode {:filter (format "content-unique-id='%s'" content-unique-id)}))
                         (ltu/body->edn)
                         (ltu/is-count 1)
                         (ltu/is-status 200)
                         (get-in [:response :body :resources])
                         first
                         :message)))

      ;; users not in the view ACL can not see the notification
      (-> (header session-anon authn-info-header "user/foo group/nuvla-user group/nuvla-anon")
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "defer")
          (ltu/is-operation-present "delete")
          (ltu/is-operation-absent "edit"))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "defer")
          (ltu/is-operation-present "delete")
          (ltu/is-operation-absent "edit"))

      ;; direct edit is not allowed
      (-> session-admin
          (request abs-uri
                   :request-method :put
                   :body (json/write-str (assoc valid-notification :message "new message")))
          (ltu/body->edn)
          (ltu/is-status 405)
          (ltu/message-matches #"(?s).*invalid method.*"))

      ;; defer by user via action
      (let [notif     (-> session-user
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200))
            defer-url (str p/service-context (ltu/get-op notif "defer"))]

        ;; bad delay value
        (-> session-user
            (request defer-url
                     :request-method :post
                     :body (json/write-str {defer-param-kw 5.0}))
            (ltu/body->edn)
            (ltu/is-status 400))

        ;; correct delay value
        (-> session-user
            (request defer-url
                     :request-method :post
                     :body (json/write-str {defer-param-kw 10}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/has-key :not-before))

        ;; delay it longer
        (-> session-user
            (request defer-url
                     :request-method :post
                     :body (json/write-str {defer-param-kw 60}))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; user can delete
        (-> session-user
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 404))))))


(deftest metadata
  (let [session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "user/jane group/nuvla-user group/nuvla-anon"))
        uri          (str p/service-context "resource-metadata/" resource-type)]
    (let [actions     (-> session-user
                          (request uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/has-key :actions)
                          (get-in [:response :body :actions]))
          delay-param (->> actions
                           (filter (fn [x] (= "defer" (:name x))))
                           first
                           :input-parameters
                           (filter (fn [x] (= defer-param-name (:name x))))
                           first)]
      (is (seq delay-param))
      (is (= delay-default (get-in delay-param [:value-scope :default]))))))