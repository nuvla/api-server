(ns sixsq.nuvla.server.resources.hook-performance-report-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.hook :as t]
    [sixsq.nuvla.server.resources.hook-performance-report :as hpr]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type "/" hpr/action))


(def session-template-base-uri (str p/service-context st/resource-type))

(deftest lifecycle
  (let [app           (ltu/ring-app)
        session-json  (content-type (session app) "application/json")
        session-anon  (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-admin (header session-json authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    (-> session-anon
        (request base-uri)
        (ltu/is-status 403))

    (println
      (-> session-admin
         (request base-uri)
         (ltu/is-status 200)
          ltu/body))))
