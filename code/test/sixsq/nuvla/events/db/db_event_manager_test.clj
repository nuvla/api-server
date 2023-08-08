(ns sixsq.nuvla.events.db.db-event-manager-test
  (:require [clojure.test :refer [deftest use-fixtures]]
            [sixsq.nuvla.events.event-manager-test :as event-manager-test]
            [sixsq.nuvla.events.db.db-event-manager :as t]
            [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest check-add-event
  (event-manager-test/check-add-event (t/->DbEventManager)))


(deftest check-search-events
  (event-manager-test/check-search-events (t/->DbEventManager)))


(deftest crud-add-wrapper
  (event-manager-test/check-crud-add-wrapper (t/->DbEventManager)))


(deftest crud-edit-wrapper
  (event-manager-test/check-crud-edit-wrapper (t/->DbEventManager)))


(deftest crud-delete-wrapper
  (event-manager-test/check-crud-delete-wrapper (t/->DbEventManager)))


(deftest crud-action-wrapper
  (event-manager-test/check-action-wrapper (t/->DbEventManager)))


(deftest check-disable-events
  (event-manager-test/check-disable-events (t/->DbEventManager)))

