(ns com.sixsq.nuvla.server.resources.callback-module-update-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback-module-update :as cmu]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.notification :as notif]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context callback/resource-type))

(def module-base-uri (str p/service-context module/resource-type))

(def timestamp "2000-00-00T00:00:00.00Z")

(def image-name "image")
(def old-image-tag "0.1")
(def new-image-tag "0.2")
(def old-commit-msg (format "initial image %s:%s" image-name old-image-tag))
(def new-commit-msg (format "new image %s:%s" image-name new-image-tag))

(def module-entry {:id                        (str module/resource-type "/connector-uuid")
                   :resource-type             module/resource-type
                   :created                   timestamp
                   :updated                   timestamp
                   :path                      "a/b"
                   :subtype                   "component"

                   :logo-url                  "https://example.org/logo"

                   :data-accept-content-types ["application/json" "application/x-something"]
                   :data-access-protocols     ["http+s3" "posix+nfs"]})

(def module-content {:author        "someone"
                     :commit        old-commit-msg
                     :image         {:image-name image-name
                                     :tag        old-image-tag}
                     :architectures ["amd64" "arm/v6"]
                     :ports         [{:protocol       "tcp"
                                      :target-port    22
                                      :published-port 8022}]})

(deftest callback-module-update
  (let [session-anon        (-> (session (ltu/ring-app))
                                (content-type "application/json"))
        session-admin       (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user        (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        _project-module (-> session-user
                            (request module-base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string
                                             {:subtype "project"
                                              :path    "a"}))
                            (ltu/body->edn)
                            (ltu/is-status 201))
        module-resource     (-> session-user
                                (request module-base-uri
                                         :request-method :post
                                         :body (j/write-value-as-string (assoc module-entry :content module-content)))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location))

        create-callback     {:action          cmu/action-name
                             :target-resource {:href module-resource}
                             :data            {:image  {:image-name image-name
                                                        :tag        new-image-tag}
                                               :commit new-commit-msg}}

        callback-id         (-> session-admin
                                (request base-uri
                                         :request-method :post
                                         :body (j/write-value-as-string create-callback))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/body)
                                :resource-id)

        callback-uri        (str p/service-context callback-id)

        create-notification {:callback callback-id
                             :category "x"
                             :message "x"
                             :content-unique-id "x"}

        notification-id     (-> session-admin
                                (request (str p/service-context notif/resource-type)
                                         :request-method :post
                                         :body (j/write-value-as-string create-notification))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/body)
                                :resource-id)

        notification-uri    (str p/service-context notification-id)

        callback-trigger    (str p/service-context (-> session-admin
                                                       (request callback-uri)
                                                       (ltu/body->edn)
                                                       (ltu/is-status 200)
                                                       (ltu/get-op "execute")))]

    ;; notification exist
    (-> session-admin
        (request notification-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    (-> session-admin
        (request callback-trigger)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; notification has been deleted after execution of the callback
    (-> session-admin
        (request notification-uri)
        (ltu/body->edn)
        (ltu/is-status 404))

    (let [new-content (-> session-admin
                          (request (str p/service-context module-resource))
                          (ltu/body->edn)
                          (ltu/body)
                          :content)]
      (is (= (-> new-content :commit) new-commit-msg))
      (is (= (-> new-content :image :image-name) image-name))
      (is (= (-> new-content :image :tag) new-image-tag)))))
