(ns sixsq.nuvla.server.utils-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.utils :as r])
  (:import
    (clojure.lang ExceptionInfo)))


(deftest check-response-created
  (let [id "RESOURCE_ID"
        r (r/response-created id)]
    (is (= 201 (:status r)))
    (is (= id (get-in r [:headers "Location"])))
    (is (nil? (:cookies r))))

  (let [id "RESOURCE_ID"
        cookie-name "MY_COOKIE"
        cookie-value "MY_COOKIE_VALUE"
        r (r/response-created id [cookie-name cookie-value])]
    (is (= 201 (:status r)))
    (is (= id (get-in r [:headers "Location"])))
    (is (= cookie-value (get-in r [:cookies cookie-name])))))


(deftest check-response-final-redirect
  (let [location "collection/my-new-resource"
        r (r/response-final-redirect location)]
    (is (= 303 (:status r)))
    (is (= location (get-in r [:headers "Location"])))
    (is (nil? (:cookies r))))

  (let [location "RESOURCE_ID"
        cookie-name "MY_COOKIE"
        cookie-value "MY_COOKIE_VALUE"
        r (r/response-final-redirect location [cookie-name cookie-value])]
    (is (= 303 (:status r)))
    (is (= location (get-in r [:headers "Location"])))
    (is (= cookie-value (get-in r [:cookies cookie-name])))))


(deftest check-json-response
  (let [body {:key "value"}
        response (r/json-response body)]
    (is (= 200 (:status response)))
    (is (= body (:body response)))
    (is (= "application/json" (get-in response [:headers "Content-Type"])))))


(deftest check-map-response
  (let [msg "ok"
        status 123
        id "collection/resource-id"
        location "collection/new-resource-id"]

    (are [args expected] (= expected (apply r/map-response args))

                         [msg status]
                         {:status  status
                          :headers {"Content-Type" "application/json"}
                          :body    {:message msg
                                    :status  status}}

                         [msg status id]
                         {:status  status
                          :headers {"Content-Type" "application/json"}
                          :body    {:message     msg
                                    :status      status
                                    :resource-id id}}

                         [msg status id location]
                         {:status  status
                          :headers {"Content-Type" "application/json"
                                    "Location"     location}
                          :body    {:message     msg
                                    :status      status
                                    :resource-id id}})))


(deftest check-ex-response
  (let [msg "ok"
        status 123
        id "collection/resource-id"
        location "collection/new-resource-id"]

    (let [ex (r/ex-response msg status)]
      (is (instance? ExceptionInfo ex))
      (is (= msg (.getMessage ex))))

    (are [args expected] (= expected (ex-data (apply r/ex-response args)))

                         [msg status]
                         {:status  status
                          :headers {"Content-Type" "application/json"}
                          :body    {:message msg
                                    :status  status}}

                         [msg status id]
                         {:status  status
                          :headers {"Content-Type" "application/json"}
                          :body    {:message     msg
                                    :status      status
                                    :resource-id id}}

                         [msg status id location]
                         {:status  status
                          :headers {"Content-Type" "application/json"
                                    "Location"     location}
                          :body    {:message     msg
                                    :status      status
                                    :resource-id id}})))


(deftest check-response-deleted
  (let [id "collection/resource-id"
        response (r/response-deleted id)
        msg (str id " deleted")]
    (is (= 200 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-response-updated
  (let [id "collection/resource-id"
        response (r/response-updated id)
        msg (str "updated " id)]
    (is (= 200 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-response-not-found
  (let [id "collection/resource-id"
        response (r/response-not-found id)
        msg (str id " not found")]
    (is (= 404 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-response-error
  (let [msg "BAD THING HAPPENED"
        response (r/response-error msg)]
    (is (= 500 (:status response)))
    (is (pos? (str/index-of (-> response :body :message) msg)))))


(deftest check-response-conflict
  (let [id "collection/resource-id"
        response (r/response-conflict id)
        msg (str "conflict with " id)]
    (is (= 409 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-ex-bad-request
  (let [response (ex-data (r/ex-bad-request))]
    (is (= 400 (:status response)))
    (is (= "invalid request" (-> response :body :message)))

    (let [msg "bad-thing"
          response (ex-data (r/ex-bad-request msg))]
      (is (= 400 (:status response)))
      (is (= msg (-> response :body :message))))))


(deftest check-ex-not-found
  (let [id "collection/resource-id"
        response (ex-data (r/ex-not-found id))
        msg (str id " not found")]
    (is (= 404 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-ex-conflict
  (let [id "collection/resource-id"
        response (ex-data (r/ex-conflict id))
        msg (str "conflict with " id)]
    (is (= 409 (:status response)))
    (is (= msg (-> response :body :message)))))


(deftest check-ex-unauthorized
  (let [id "collection/resource-id"
        response (ex-data (r/ex-unauthorized id))]
    (is (= 403 (:status response)))
    (is (pos? (str/index-of (-> response :body :message) id))))

  (let [response (ex-data (r/ex-unauthorized nil))]
    (is (= 403 (:status response)))
    (is (= "credentials required" (-> response :body :message)))))


(deftest check-ex-bad-method
  (let [uri "collection/resource-id"
        method "HEAD"
        response (ex-data (r/ex-bad-method {:uri uri, :request-method method}))]

    (is (= 405 (:status response)))
    (is (zero? (str/index-of (-> response :body :message) "invalid method (")))
    (is (pos? (str/index-of (-> response :body :message) uri)))
    (is (pos? (str/index-of (-> response :body :message) method)))

    (let [response (ex-data (r/ex-bad-method {:uri uri}))]
      (is (= 405 (:status response)))
      (is (zero? (str/index-of (-> response :body :message) "invalid method (")))
      (is (pos? (str/index-of (-> response :body :message) uri)))
      (is (nil? (str/index-of (-> response :body :message) method))))

    (let [response (ex-data (r/ex-bad-method {:request-method method}))]
      (is (= 405 (:status response)))
      (is (zero? (str/index-of (-> response :body :message) "invalid method (")))
      (is (nil? (str/index-of (-> response :body :message) uri)))
      (is (pos? (str/index-of (-> response :body :message) method))))))


(deftest check-ex-bad-action
  (let [uri "collection/resource-id"
        method "HEAD"
        action "DO_IT"
        response (ex-data (r/ex-bad-action {:uri uri, :request-method method} action))]

    (is (= 404 (:status response)))
    (is (zero? (str/index-of (-> response :body :message) "undefined action (")))
    (is (pos? (str/index-of (-> response :body :message) uri)))
    (is (pos? (str/index-of (-> response :body :message) method)))
    (is (pos? (str/index-of (-> response :body :message) action)))

    (let [response (ex-data (r/ex-bad-action {:uri uri} action))]
      (is (= 404 (:status response)))
      (is (zero? (str/index-of (-> response :body :message) "undefined action (")))
      (is (pos? (str/index-of (-> response :body :message) uri)))
      (is (nil? (str/index-of (-> response :body :message) method)))
      (is (pos? (str/index-of (-> response :body :message) action))))

    (let [response (ex-data (r/ex-bad-action {:request-method method} action))]
      (is (= 404 (:status response)))
      (is (zero? (str/index-of (-> response :body :message) "undefined action (")))
      (is (nil? (str/index-of (-> response :body :message) uri)))
      (is (pos? (str/index-of (-> response :body :message) method)))
      (is (pos? (str/index-of (-> response :body :message) action))))

    (let [response (ex-data (r/ex-bad-action {:uri uri, :request-method method} nil))]
      (is (= 404 (:status response)))
      (is (zero? (str/index-of (-> response :body :message) "undefined action (")))
      (is (pos? (str/index-of (-> response :body :message) uri)))
      (is (pos? (str/index-of (-> response :body :message) method)))
      (is (nil? (str/index-of (-> response :body :message) action))))))


(deftest check-ex-bad-CIMI-filter
  (let [err {:key "value"}
        response (ex-data (r/ex-bad-CIMI-filter err))]
    (is (= 400 (:status response)))
    (is (pos? (str/index-of (-> response :body :message) (pr-str err))))))


(deftest check-ex-redirect
  (let [msg "MESSAGE"
        id "collection/resource-id"
        redirect-uri "somewhere/else"
        response (ex-data (r/ex-redirect msg id redirect-uri))]
    (is (= 303 (:status response)))
    (is (zero? (str/index-of (-> response :body :message) msg)))
    (let [location (get-in response [:headers "Location"])]
      (is (nat-int? (str/index-of location redirect-uri)))
      (is (nat-int? (str/index-of location msg))))))
