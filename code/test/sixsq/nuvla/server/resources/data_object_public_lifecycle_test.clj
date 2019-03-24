(ns sixsq.nuvla.server.resources.data-object-public-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest join-fixtures use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as data-obj]
    [sixsq.nuvla.server.resources.data-object-lifecycle-test-utils :as do-ltu]
    [sixsq.nuvla.server.resources.data-object-template :as data-obj-tpl]
    [sixsq.nuvla.server.resources.data-object-template-public :as data-obj-tpl-public]
    [sixsq.nuvla.server.resources.data.utils :as s3]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu])
  (:import
    (com.amazonaws AmazonServiceException)))


(use-fixtures :once (join-fixtures [ltu/with-test-server-fixture
                                    do-ltu/create-s3-credential!
                                    do-ltu/s3-redefs!]))


(def base-uri (str p/service-context data-obj/resource-type))


(defn data-object
  []
  {:bucket       "my-bucket"
   :credential   do-ltu/*s3-credential-id*
   :content-type "application/gzip"
   :object       "my/public-obj/name-1"})


(deftest lifecycle
  (do-ltu/full-eo-lifecycle (str p/service-context data-obj-tpl/resource-type "/" data-obj-tpl-public/data-object-type)
                            (data-object)))


(defn throw-any-aws-exception [_ _ _]
  (let [ex (doto
             (AmazonServiceException. "Simulated AWS Exception")
             (.setStatusCode 400))]
    (throw ex)))


(defn delete-s3-object-not-found [_ _]
  (let [ex (doto
             (AmazonServiceException. "Simulated AWS Exception for object missing on S3")
             (.setStatusCode 404))]
    (throw ex)))


(defn delete-s3-bucket-not-empty [_ _]
  (let [ex (doto
             (AmazonServiceException. "Simulated AWS Exception for deletion of not empty S3 bucket")
             (.setStatusCode 409))]
    (throw ex)))


(deftest check-public-access
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header do-ltu/user-info-header)
        base-uri (str p/service-context data-obj/resource-type)
        template (do-ltu/get-template (str p/service-context data-obj-tpl/resource-type "/" data-obj-tpl-public/data-object-type))

        create-href {:template (-> (data-object)
                                   (assoc :bucket "my-public-bucket") ;; to avoid conflict with existing data-object
                                   (assoc :href (:id template))
                                   (dissoc :type))}]

    ;; Create the test object.
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-href))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))

    (let [entry (-> session-user
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri data-obj/collection-type)
                    (ltu/is-count 1)
                    (ltu/entries)
                    first)
          id (:id entry)
          abs-uri (str p/service-context id)
          upload-op (-> session-user
                        (request abs-uri)
                        (ltu/body->edn)
                        (ltu/is-operation-present "upload")
                        (ltu/is-operation-present "delete")
                        (ltu/is-operation-present "edit")
                        (ltu/is-operation-absent "ready")
                        (ltu/is-operation-absent "download")
                        (ltu/is-status 200)
                        (ltu/get-op "upload"))

          abs-upload-uri (str p/service-context upload-op)]

      ;; Upload object's content.
      (-> session-user
          (request abs-upload-uri
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [uploading-eo (-> session-user
                             (request abs-uri)
                             (ltu/body->edn)
                             (ltu/is-operation-present "ready")
                             (ltu/is-status 200))

            ready-url-action (str p/service-context (ltu/get-op uploading-eo "ready"))]


        ;; Missing ACL should fail the action
        (with-redefs [s3/set-acl-public-read throw-any-aws-exception]
          (-> session-user
              (request ready-url-action
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 500)))


        ;; With public ACL the public url should be set on ready action
        (with-redefs [s3/set-acl-public-read (fn [_ _ _] nil)
                      s3/s3-url (fn [_ _ _] "https://my-object.s3.com")]
          (-> session-user
              (request ready-url-action
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-key-value :url "https://my-object.s3.com")
              (ltu/is-status 200)))

        ;; Must delete the created object to avoid conflict with other tests.
        (with-redefs [s3/bucket-exists? (fn [_ _] true)
                      s3/delete-s3-object delete-s3-object-not-found
                      s3/delete-s3-bucket delete-s3-bucket-not-empty]
          (-> session-user
              (request abs-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id data-obj/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))


