(ns sixsq.nuvla.server.resources.spec.job
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-operation :as cimi-common-operation]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))

(def job-href-regex #"^job/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches job-href-regex %)))

(s/def ::state #{"QUEUED" "RUNNING" "FAILED" "SUCCESS" "STOPPING" "STOPPED"})
(s/def ::target-resource ::cimi-common/resource-link)
(s/def ::affected-resources ::cimi-common/resource-links)
(s/def ::return-code int?)
(s/def ::progress (s/int-in 0 101))
(s/def ::time-of-status-change ::cimi-core/timestamp)
(s/def ::status-message string?)
(s/def ::action ::cimi-common-operation/rel)
(s/def ::parent-job ::href)
(s/def ::nested-jobs (s/coll-of ::href))
; An optional priority as an integer with at most 3 digits. Lower values signify higher priority.
(s/def ::priority (s/int-in 0 1000))
(s/def ::started ::cimi-core/timestamp)
(s/def ::duration (s/nilable nat-int?))


(s/def ::job
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::state
                               ::action
                               ::progress]
                      :opt-un [::target-resource
                               ::affected-resources
                               ::return-code
                               ::status-message
                               ::time-of-status-change
                               ::parent-job
                               ::nested-jobs
                               ::priority
                               ::started
                               ::duration]}))
