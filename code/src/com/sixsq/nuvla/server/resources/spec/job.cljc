(ns com.sixsq.nuvla.server.resources.spec.job
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.job.utils :as utils]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.common-operation :as common-operation]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::state
  (-> (st/spec utils/states)
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "job state"
             :json-schema/value-scope {:values (vec utils/states)}

             :json-schema/order 20)))


(s/def ::target-resource
  (-> (st/spec ::core/resource-link)
      (assoc :name "target-resource"
             :json-schema/type "map"
             :json-schema/display-name "target resource"
             :json-schema/description "reference of target resource"

             :json-schema/order 21)))


(s/def ::affected-resources
  (-> (st/spec ::core/resource-links)
      (assoc :name "affected-resources"
             :json-schema/type "array"
             :json-schema/display-name "affected resources"
             :json-schema/description "references to affected resources"

             :json-schema/order 22)))


(s/def ::return-code
  (-> (st/spec nat-int?)
      (assoc :name "return-code"
             :json-schema/type "integer"
             :json-schema/display-name "return code"
             :json-schema/description "return code from task represented by job (0 is success)"

             :json-schema/order 23)))


(s/def ::progress
  (-> (st/spec (s/int-in 0 101))
      (assoc :name "progress"
             :json-schema/type "integer"
             :json-schema/description "progress indication from task (0-100%)"

             :json-schema/order 24)))


(s/def ::time-of-status-change
  (-> (st/spec ::core/timestamp)
      (assoc :name "time-of-status-change"
             :json-schema/display-name "time of status change"
             :json-schema/description "time when the status of job changed; may be different from when the job resource was updated"

             :json-schema/order 25)))


(s/def ::status-message
  (-> (st/spec string?)
      (assoc :name "status-message"
             :json-schema/display-name "status message"
             :json-schema/description "additional information about the job's current status"
             :json-schema/type "free-text"
             :json-schema/indexed false

             :json-schema/order 26)))


(s/def ::action
  (-> (st/spec ::common-operation/rel)
      (assoc :name "action"
             :json-schema/type "string"
             :json-schema/description "URI identifying the action"

             :json-schema/order 27)))


(def job-href-regex #"^job/[a-z0-9]+(-[a-z0-9]+)*$")


(s/def ::href (s/and string? #(re-matches job-href-regex %)))


(s/def ::parent-job
  (-> (st/spec ::href)
      (assoc :name "parent-job"
             :json-schema/type "string"
             :json-schema/display-name "parent job"
             :json-schema/description "identifier of parent job"

             :json-schema/order 28)))


(s/def ::nested-jobs
  (-> (st/spec (s/coll-of ::href :kind vector?))
      (assoc :name "nested-jobs"
             :json-schema/type "array"
             :json-schema/display-name "nested jobs"
             :json-schema/description "identifier of nested (child) jobs"

             :json-schema/order 29)))


(s/def ::priority
  (-> (st/spec (s/int-in 0 1000))
      (assoc :name "priority"
             :json-schema/type "integer"
             :json-schema/description "priority as an integer in range [0, 999]; lower values are higher priorities"

             :json-schema/order 30)))


(s/def ::started
  (-> (st/spec ::core/timestamp)
      (assoc :name "started"
             :json-schema/description "timestamp indicating the moment the job was started"

             :json-schema/order 31)))


(s/def ::duration
  (-> (st/spec nat-int?)
      (assoc :name "duration"
             :json-schema/type "integer"
             :json-schema/description "duration (in seconds) to complete the task"

             :json-schema/order 32)))


(s/def ::expiry
  (-> (st/spec ::core/timestamp)
      (assoc :name "expiry"
             :json-schema/type "date-time"

             :json-schema/description "expiry timestamp after which the job can be cleaned up"
             :json-schema/group "body"
             :json-schema/order 33)))


(s/def ::execution-mode
  (-> (st/spec #{"pull" "push" "mixed"})
    (assoc :name "execution-mode"
           :json-schema/type "string"
           :json-schema/description "job execution mode"
           :json-schema/value-scope {:values ["pull" "push" "mixed"]}

           :json-schema/order 34)))


(s/def ::output
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "output"
           :json-schema/display-name "output message"
           :json-schema/description "full output (including traceback) from the job execution"

           :json-schema/order 35)))


(s/def ::payload
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "payload"
           :json-schema/display-name "job payload"
           :json-schema/description "JSON-compliant string to be passed to the job, such as execution arguments"

           :json-schema/order 36)))


(s/def ::mec-operation-type
  (-> (st/spec #{"INSTANTIATE" "TERMINATE" "OPERATE"})
      (assoc :name "mec-operation-type"
             :json-schema/type "string"
             :json-schema/description "ETSI MEC lifecycle operation type"
             :json-schema/value-scope {:values ["INSTANTIATE" "TERMINATE" "OPERATE"]}

             :json-schema/order 37)))


(s/def ::mec-app-instance-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "mec-app-instance-id"
             :json-schema/type "resource-id"
             :json-schema/description "deployment-backed MEC app instance identifier"

             :json-schema/order 38)))


(s/def ::mec-request-params
  (-> (st/spec map?)
      (assoc :name "mec-request-params"
             :json-schema/type "map"
             :json-schema/description "MEC lifecycle request parameters associated with this job"

             :json-schema/order 39)))


(s/def ::mepm-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "mepm-id"
             :json-schema/type "resource-id"
             :json-schema/description "identifier of the MEPM selected for the MEC lifecycle action"

             :json-schema/order 40)))


(s/def ::mepm-endpoint
  (-> (st/spec ::core/url)
      (assoc :name "mepm-endpoint"
             :json-schema/type "string"
             :json-schema/description "Mm3 endpoint of the selected MEPM"

             :json-schema/order 41)))


(s/def ::mec-host-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "mec-host-id"
             :json-schema/type "resource-id"
             :json-schema/description "target MEC host associated with the lifecycle job"

             :json-schema/order 42)))


(s/def ::mec-last-notification
  (-> (st/spec map?)
      (assoc :name "mec-last-notification"
             :json-schema/type "map"
             :json-schema/description "summary of the most recent southbound Mm3.003 lifecycle notification correlated to this job"

             :json-schema/order 43)))


(s/def ::mec-southbound-operation-id
  (-> (st/spec string?)
      (assoc :name "mec-southbound-operation-id"
             :json-schema/type "string"
             :json-schema/description "southbound Mm3 lifecycle operation identifier correlated with this job"

             :json-schema/order 44)))


(s/def ::version
  (-> (st/spec nat-int?)
    (assoc :name "version"
           :json-schema/type "integer"
           :json-schema/description "Compatibility indicator which represent Job-engine major version"

           :json-schema/order 45)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::state
                               ::action
                               ::progress
                               ::execution-mode
                               ::version]
                      :opt-un [::target-resource
                               ::affected-resources
                               ::return-code
                               ::status-message
                               ::time-of-status-change
                               ::parent-job
                               ::nested-jobs
                               ::priority
                               ::started
                               ::duration
                               ::expiry
                               ::output
                               ::payload
                               ::mec-operation-type
                               ::mec-app-instance-id
                               ::mec-request-params
                               ::mepm-id
                               ::mepm-endpoint
                               ::mec-host-id
                               ::mec-last-notification
                               ::mec-southbound-operation-id]}))
