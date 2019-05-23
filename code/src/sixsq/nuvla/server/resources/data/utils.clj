(ns sixsq.nuvla.server.resources.data.utils
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-exoscale :as exoscale]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio :as minio]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time])
  (:import
    (com.amazonaws AmazonServiceException HttpMethod SdkClientException)
    (com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials)
    (com.amazonaws.client.builder AwsClientBuilder$EndpointConfiguration)
    (com.amazonaws.services.s3 AmazonS3ClientBuilder)
    (com.amazonaws.services.s3.model CannedAccessControlList CreateBucketRequest
                                     DeleteBucketRequest DeleteObjectRequest
                                     GeneratePresignedUrlRequest HeadBucketRequest)))


(def ^:const default-ttl 15)


(defn log-aws-exception
  [amazon-exception]
  (let [status  (.getStatusCode amazon-exception)
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
  (let [endpoint    (AwsClientBuilder$EndpointConfiguration. endpoint "us-east-1")
        credentials (AWSStaticCredentialsProvider. (BasicAWSCredentials. key secret))]
    (-> (AmazonS3ClientBuilder/standard)
        (.withEndpointConfiguration endpoint)
        (.withPathStyleAccessEnabled true)
        (.withCredentials credentials)
        .build)))


(defn generate-url
  [obj-store-conf bucket obj-name verb & [{:keys [ttl content-type]}]]
  (let [expiration (-> (or ttl default-ttl)
                       (time/from-now :minutes)
                       (time/java-date))
        method     (if (= verb :put)
                     HttpMethod/PUT
                     HttpMethod/GET)
        req        (doto (GeneratePresignedUrlRequest. bucket obj-name)
                     (.setMethod method)
                     (.setExpiration expiration))]
    (cond
      content-type (.setContentType req content-type))
    (str (.generatePresignedUrl (get-s3-client obj-store-conf) req))))


(defn delete-s3-object
  "Mocked in unit tests. Externalized from function below to allow for
   exceptions to be caught."
  [s3-client deleteRequest]
  (.deleteObject s3-client deleteRequest))


(defn try-delete-s3-object [s3-creds bucket object]
  (let [deleteRequest (DeleteObjectRequest. bucket object)]
    (try-catch-aws-fn
      (delete-s3-object (get-s3-client s3-creds) deleteRequest))))


(defn delete-s3-bucket
  "Mocked in unit tests. Externalized from function below to allow for
   exceptions to be caught."
  [s3-client deleteRequest]
  (.deleteBucket s3-client deleteRequest))


(defn try-delete-s3-bucket [s3-creds bucket]
  (let [deleteRequest (DeleteBucketRequest. bucket)]
    (try-catch-aws-fn
      (delete-s3-bucket (get-s3-client s3-creds) deleteRequest))))


(defn bucket-exists?
  "Function mocked in unit tests"
  [s3-client bucket]
  (.doesBucketExist s3-client bucket))


(defn head-bucket
  "This method returns a HeadBucketResult if the bucket exists and you have
  permission to access it. Otherwise, the method will throw an
  AmazonServiceException with status code '404 Not Found' if the bucket does
  not exist, '403 Forbidden' if the user does not have access to the bucket, or
  '301 Moved Permanently' if the bucket is in a different region than the
  client is configured with"
  [s3-client bucket]
  (.headBucket s3-client (HeadBucketRequest. bucket)))


(defn try-head-bucket [s3-client bucket]
  (try-catch-aws-fn (head-bucket s3-client bucket)))


(defn create-bucket!
  "Caller should have checked that bucket does not exist yet. If creation
  fails, an Exception is thrown; otherwise true is returned. Mocked in unit
  tests."
  [s3-client bucket]
  (.createBucket s3-client (CreateBucketRequest. bucket))
  true)


(defn extract-s3-key-secret
  "Returns a tuple with the key and secret for accessing the S3 service. There
   are many subtypes of credentials, so the fields are different in each case."
  [{:keys [subtype] :as credential}]

  ;; FIXME: Find a better solution for dispatching on credential subtype.
  (cond
    (= minio/credential-subtype subtype) [(:access-key credential)
                                          (:secret-key credential)]
    (= exoscale/credential-subtype subtype) [(:exoscale-api-key credential)
                                             (:exoscale-api-secret-key credential)]
    :else nil))


(defn extract-s3-endpoint
  "Returns the endpoint of the S3 service referenced by the credential."
  [{:keys [parent] :as credential}]
  (->> parent
       crud/retrieve-by-id-as-admin
       :endpoint))


(defn uploadable-bucket?
  "When the bucket exists, but the user can't create the object. The data
  object resource must not be created."
  [s3-client bucket]
  (try-head-bucket s3-client bucket)                        ;;throw exception if not authorized
  true)


(defn credential->s3-client-cfg
  "Need subtype to dispatch on when loading credentials."
  [s3-cred-id]
  (when s3-cred-id
    (let [credential (crud/retrieve-by-id-as-admin s3-cred-id)
          [key secret] (extract-s3-key-secret credential)
          endpoint   (extract-s3-endpoint credential)]
      (when (and key secret endpoint)
        {:key      key
         :secret   secret
         :endpoint endpoint}))))


(defn ensure-bucket-exists
  "This function ensures that the bucket that we're using to exists and can be
   written to. If it doesn't exist, this function will create it. This function
   throws an exception when either the bucket isn't writable or can't be
   created."
  [{:keys [bucket credential] :as resource} request]
  (let [s3-client (some-> credential
                          credential->s3-client-cfg
                          get-s3-client)]

    (if (bucket-exists? s3-client bucket)
      (do
        (uploadable-bucket? s3-client bucket)
        resource)
      (try
        (create-bucket! s3-client bucket)
        resource
        (catch Exception e
          (logu/log-and-throw 503 (format "Unable to create the bucket %s" bucket)))))))


(defn s3-object-metadata
  "See https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/ObjectMetadata.html
  Although most of metadata keys are yet unused, they are provided for documentation purpose."
  [s3-client bucket object]
  (let [meta (try-catch-aws-fn (.getObjectMetadata s3-client bucket object))]
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


(defn add-s3-bytes
  "Adds a size attribute to external object if present in metadata
  or returns untouched data object. Ignore any S3 exception "
  [{:keys [credential bucket object] :as resource}]
  (let [s3-client (-> credential
                      (credential->s3-client-cfg)
                      (get-s3-client))
        bytes     (try
                    (:contentLength (s3-object-metadata s3-client bucket object))
                    (catch Exception _
                      (log/warn (str "Could not access the metadata for S3 object " object))))]
    (cond-> resource
            bytes (assoc :bytes bytes))))


(defn add-s3-md5sum
  "Adds a md5sum attribute to data object if present in metadata
  or returns untouched data object. Ignore any S3 exception"
  [{:keys [credential bucket object] :as resource}]
  (let [s3-client (-> credential
                      (credential->s3-client-cfg)
                      (get-s3-client))
        md5       (try
                    (:contentMD5 (s3-object-metadata s3-client bucket object))
                    (catch Exception _
                      (log/warn (str "Could not access the metadata for S3 object " object))))]
    (cond-> resource
            md5 (assoc :md5sum md5))))


;; Function separated to allow for mocking in unit tests.
(defn set-acl-public-read
  [s3-client bucket object]
  (.setObjectAcl s3-client bucket object CannedAccessControlList/PublicRead))


(defn try-set-public-read-object
  [s3-client bucket object]
  (try
    (try-catch-aws-fn (set-acl-public-read s3-client bucket object))
    (catch Exception _
      (logu/log-and-throw 500 (str "Exception while setting S3 ACL on object " object)))))


(defn set-public-read-object
  "Returns the untouched resource. Side effect is only on S3 permissions"
  [{:keys [credential bucket object] :as resource}]
  (let [s3-client (-> credential
                      (credential->s3-client-cfg)
                      (get-s3-client))]
    (try-set-public-read-object s3-client bucket object)
    resource))


(defn s3-url
  [s3-client bucket object]
  (str (.getUrl s3-client bucket object)))


(defn add-s3-url
  [{:keys [credential bucket object] :as resource}]
  (let [s3-client (-> credential
                      (credential->s3-client-cfg)
                      (get-s3-client))]
    (assoc resource :url (s3-url s3-client bucket object))))
