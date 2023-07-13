(ns sixsq.nuvla.server.resources.api-docs-lifecycle-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.api-docs :as api-docs]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu])
  (:import (io.swagger.parser OpenAPIParser)
           (io.swagger.v3.parser.core.models ParseOptions SwaggerParseResult)))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context api-docs/resource-type))


(deftest lifecycle-api-docs
  (let [;; fetch the openapi spec with the anon user, spec must be publicly exposed
        session-anon                (-> (session (ltu/ring-app))
                                        (content-type "application/json"))
        swagger-json                (-> session-anon
                                        (request base-uri)
                                        (ltu/is-status 200)
                                        (get-in [:response :body]))
        ^ParseOptions parse-options (doto (ParseOptions.)
                                          (.setValidateInternalRefs true))
        ;; validate the returned spec with the official Swagger parser
        ^SwaggerParseResult result  (-> (OpenAPIParser.)
                                        (.readContents swagger-json nil parse-options))]
    (println swagger-json)
    (is (empty? (.getMessages result)))))
