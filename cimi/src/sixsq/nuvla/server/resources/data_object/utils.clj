(ns sixsq.nuvla.server.resources.data-object.utils
  (:require
    [clj-time.coerce :as tc]
    [clj-time.core :as t]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.util.log :as logu])
  (:import
    (com.amazonaws AmazonServiceException HttpMethod SdkClientException)
    (com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials)
    (com.amazonaws.client.builder AwsClientBuilder$EndpointConfiguration)
    (com.amazonaws.services.s3 AmazonS3ClientBuilder)
    (com.amazonaws.services.s3.model CannedAccessControlList CreateBucketRequest
                                     DeleteBucketRequest DeleteObjectRequest GeneratePresignedUrlRequest HeadBucketRequest)))


(def ^:const default-ttl 15)


(def request-admin {:identity                      {:current         "internal"
                                                    :authentications {"internal" {:roles    #{"ADMIN"}
                                                                                  :identity "internal"}}}
                    :sixsq.slipstream.authn/claims {:username "internal"
                                                    :roles    "ADMIN"}
                    :params                        {:resource-name "user"}
                    :route-params                  {:resource-name "user"}
                    :user-roles                    #{"ANON"}})


(defn log-aws-exception
  [amazon-exception]
  (let [status (.getStatusCode amazon-exception)
        message (.getMessage amazon-exception)]
    (logu/log-and-throw status message)))


(defn log-sdk-exception
  [sdk-exception]
  (logu/log-and-throw-400 (.getMessage sdk-exception)))


(defmacro try-catch-aws-fn
  ([body]
   `(try ~body
         (catch AmazonServiceException ae#
           (log-aws-exception ae#))
         (catch SdkClientException se#
           (log-sdk-exception se#))
         (catch Exception e#
           (logu/log-and-throw-400 (.getMessage e#))))))


(defn get-s3-client
  [{:keys [key secret endpoint]}]
  (let [endpoint (AwsClientBuilder$EndpointConfiguration. endpoint "us-east-1")
        credentials (AWSStaticCredentialsProvider. (BasicAWSCredentials. key secret))]
    (-> (AmazonS3ClientBuilder/standard)
        (.withEndpointConfiguration endpoint)
        (.withPathStyleAccessEnabled true)
        (.withCredentials credentials)
        .build)))


(defn generate-url
  [obj-store-conf bucket obj-name verb & [{:keys [ttl content-type]}]]
  (let [expiration (tc/to-date (-> (or ttl default-ttl) t/minutes t/from-now))
        method (if (= verb :put)
                 HttpMethod/PUT
                 HttpMethod/GET)
        req (doto (GeneratePresignedUrlRequest. bucket obj-name)
              (.setMethod method)
              (.setExpiration expiration))]
    (cond
      content-type (.setContentType req content-type))
    (str (.generatePresignedUrl (get-s3-client obj-store-conf) req))))


(defn delete-s3-object
  "Mocked in unit tests. Externalized from function below to allow for
   exceptions to be caught."
  [s3client deleteRequest]
  (.deleteObject s3client deleteRequest))


(defn try-delete-s3-object [s3-creds bucket object]
  (let [deleteRequest (DeleteObjectRequest. bucket object)]
    (try-catch-aws-fn
      (delete-s3-object (get-s3-client s3-creds) deleteRequest))))

(defn delete-s3-bucket
  "Mocked in unit tests. Externalized from function below to allow for
   exceptions to be caught."
  [s3client deleteRequest]
  (.deleteBucket s3client deleteRequest))


(defn try-delete-s3-bucket [s3-creds bucket]
  (let [deleteRequest (DeleteBucketRequest. bucket)]
    (try-catch-aws-fn
      (delete-s3-bucket (get-s3-client s3-creds) deleteRequest))))


(defn bucket-exists?
  "Function mocked in unit tests"
  [s3client bucket-name]
  (.doesBucketExist s3client bucket-name))


(defn head-bucket
  "This method returns a HeadBucketResult if the bucket exists and you have
  permission to access it. Otherwise, the method will throw an
  AmazonServiceException with status code '404 Not Found' if the bucket does
  not exist, '403 Forbidden' if the user does not have access to the bucket, or
  '301 Moved Permanently' if the bucket is in a different region than the
  client is configured with"
  [s3client bucket]
  (.headBucket s3client (HeadBucketRequest. bucket)))


(defn try-head-bucket [s3client bucket]
  (try-catch-aws-fn (head-bucket s3client bucket)))


(defn create-bucket!
  "Caller should have checked that bucket does not exist yet. If creation
  fails, an Exception is thrown; otherwise true is returned. Mocked in unit
  tests."
  [s3client bucket-name]
  (.createBucket s3client (CreateBucketRequest. bucket-name))
  true)


(defn expand-cred
  "Returns credential document after expanding `href-obj-store-cred`
   credential href.

  Deriving object-type from request is not directly possible as this is a
  request on action resource. We would need to get resource id, load the
  resource and get object-type from it. Instead, requiring object-type as
  parameter. It should be known to the callers."
  [href-obj-store-cred]
  (std-crud/resolve-hrefs href-obj-store-cred request-admin true))


(defn bucket-creation-ok?
  "When the requested bucket doesn't exist and can't be created. The data
  object resource must not be created."
  [s3client bucket-name]
  (try
    (or (bucket-exists? s3client bucket-name)
        (create-bucket! s3client bucket-name))
    (catch Exception e
      (log/errorf "Error when creating bucket %s: %s" bucket-name (.getMessage e))
      false)))


(defn uploadable-bucket?
  "When the bucket exists, but the user can't create the object. The data
  object resource must not be created."
  [obj-store-conf bucket]
  (let [s3client (get-s3-client obj-store-conf)]
    (try-head-bucket s3client bucket)                       ;;throw exception if not authorized
    true))


(defn format-creds-for-s3-api
  "Need object-type to dispatch on when loading credentials."
  [href-obj-store-cred]
  (let [{:keys [key secret connector]} (expand-cred href-obj-store-cred)]
    {:key      key
     :secret   secret
     :endpoint (:objectStoreEndpoint connector)}))


(defn ok-to-add-data-resource?
  "Determines if S3 conditions are met on S3 for the user to safely add an
  external object resource. If everything is OK, then the resource itself is
  returned. Otherwise an error response map is thrown"
  [{:keys [bucket-name object-store-cred] :as resource} request]
  (let [obj-store-conf (format-creds-for-s3-api object-store-cred)
        s3client (get-s3-client obj-store-conf)]

    ;; When the requested bucket exists, but the user doesn't have permission to it :
    ;; The external object resource must not be created."
    (uploadable-bucket? obj-store-conf bucket-name)          ;; Throws if the bucket can't be written to.

    (if (bucket-creation-ok? s3client bucket-name)
      resource
      (logu/log-and-throw 503 (format "Unable to create the bucket %s" bucket-name)))))


(defn s3-object-metadata
  "See https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/ObjectMetadata.html
  Although most of metadata keys are yet unused, they are provided for documentation purpose."
  [s3client bucket object]
  (let [meta (try-catch-aws-fn (.getObjectMetadata s3client bucket object))]
    {:cacheControl       (.getCacheControl meta)
     :contentDisposition (.getContentDisposition meta)
     :contentEncoding    (.getContentEncoding meta)
     :contentLanguage    (.getContentLanguage meta)
     :contentLength      (.getContentLength meta)
     :contentMD5         (.getContentMD5 meta)
     :contentRange       (.getContentRange meta)
     :contentType        (.getContentType meta)
     :eTag               (.getETag meta)
     :expirationTime     (.getExpirationTime meta)
     :httpExpiresDate    (.getHttpExpiresDate meta)
     :instanceLength     (.getInstanceLength meta)
     :lastModified       (.getLastModified meta)
     :ongoingRestore     (.getOngoingRestore meta)
     :partCount          (.getPartCount meta)
     :rawMetadata        (.getRawMetadata meta)
     :SSEAlgorithm       (.getSSEAlgorithm meta)
     :userMetadata       (.getUserMetadata meta)
     :versionId          (.getVersionId meta)}))


(defn add-s3-size
  "Adds a size attribute to external object if present in metadata
  or returns untouched data object. Ignore any S3 exception "
  [{:keys [object-store-cred bucket-name object-name] :as resource}]
  (let [s3client (-> object-store-cred
                     (format-creds-for-s3-api)
                     (get-s3-client))
        size (try
               (:contentLength (s3-object-metadata s3client bucket-name object-name))
               (catch Exception _
                 (log/warn (str "Could not access the metadata for S3 object " object-name))))]
    (cond-> resource
            size (assoc :size size))))


(defn add-s3-md5sum
  "Adds a md5sum attribute to data object if present in metadata
  or returns untouched data object. Ignore any S3 exception"
  [{:keys [object-store-cred bucket-name object-name] :as resource}]
  (let [s3client (-> object-store-cred
                     (format-creds-for-s3-api)
                     (get-s3-client))
        md5 (try
              (:contentMD5 (s3-object-metadata s3client bucket-name object-name))
              (catch Exception _
                (log/warn (str "Could not access the metadata for S3 object " object-name))))]
    (cond-> resource
            md5 (assoc :md5sum md5))))


;; Function separated to allow for mocking in unit tests.
(defn set-acl-public-read
  [s3client bucket object]
  (.setObjectAcl s3client bucket object CannedAccessControlList/PublicRead))


(defn try-set-public-read-object
  [s3client bucket object]
  (try
    (try-catch-aws-fn (set-acl-public-read s3client bucket object))
    (catch Exception _
      (logu/log-and-throw 500 (str "Exception while setting S3 ACL on object " object)))))


(defn set-public-read-object
  "Returns the untouched resource. Side effect is only on S3 permissions"
  [{:keys [object-store-cred bucket-name object-name] :as resource}]
  (let [s3client (-> object-store-cred
                     (format-creds-for-s3-api)
                     (get-s3-client))]
    (try-set-public-read-object s3client bucket-name object-name)
    resource))


(defn s3-url
  [s3client bucket object]
  (str (.getUrl s3client bucket object)))


(defn add-s3-url
  [{:keys [object-store-cred bucket-name object-name] :as resource}]
  (let [s3client (-> object-store-cred
                     (format-creds-for-s3-api)
                     (get-s3-client))]
    (assoc resource :url (s3-url s3client bucket-name object-name))))
