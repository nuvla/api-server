(ns sixsq.nuvla.server.middleware.eventer-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def test-url (str p/service-context "session"))


(deftest event-wrapper
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        event-added   (atom false)]

    (with-redefs [sixsq.nuvla.server.resources.event.utils/add-event
                  (fn [_event] (reset! event-added true))]
      (-> session-anon
          (request test-url :request-method :post)
          (ltu/is-status 200))
      (is (true? @event-added)))))

