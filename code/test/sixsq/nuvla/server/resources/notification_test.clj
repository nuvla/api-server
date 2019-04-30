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
    [sixsq.nuvla.server.resources.notification :refer :all]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(def base-uri (str p/service-context resource-type))

(def message "message")
(def content-unique-id "content-hash")

(def valid-notification {:message           message
                         :type              "some-type"
                         :content-unique-id content-unique-id})

(use-fixtures :once ltu/with-test-server-fixture)

(deftest lifecycle
  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    ;; query: forbidden for anon
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; query: OK for user
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; create: forbidden for anon and user
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-notification))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; create: NOK with bad schema
    (doseq [[k v] {:message "" :type "A-Type" :content-unique-id ""}]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-notification k v)))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"(?s).*resource does not satisfy defined schema.*")))

    ;; lifecycle: create, find by unique id, get, defer, delete
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-notification))
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

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; direct edit is not allowed
      (-> session-admin
          (request abs-uri
                   :request-method :put
                   :body (json/write-str (assoc valid-notification :message "new message")))
          (ltu/body->edn)
          (ltu/is-status 405)
          (ltu/message-matches #"(?s).*invalid method.*"))

      ;; TODO: defer via action

      ;; delete by user is forbidden
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))

