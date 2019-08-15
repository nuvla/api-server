(ns sixsq.nuvla.server.resources.spec.deployment-log
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const deployment-id-regex #"^deployment/[0-9a-f]+(-[0-9a-f]+)*$")


(s/def ::parent (-> (st/spec (s/and string? #(re-matches deployment-id-regex %)))
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/description "reference to parent deployment resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


(s/def ::service
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "service-name"
             :json-schema/display-name "service name"
             :json-schema/description "name of Docker service"

             :json-schema/editable false
             :json-schema/order 20)))


(s/def ::since
  (-> (st/spec ::core/timestamp)
      (assoc :name "since"
             :json-schema/description "starting timestamp of the log"

             :json-schema/order 21)))


(s/def ::last-timestamp
  (-> (st/spec ::core/timestamp)
      (assoc :name "since"
             :json-schema/description "timestamp of the most recent line in the log"

             :json-schema/order 22)))


(s/def ::head-or-tail
  (-> (st/spec (s/and string? #{"head" "tail" "all"}))
      (assoc :name "head-or-tail"
             :json-schema/type "string"
             :json-schema/display-name "head or tail"
             :json-schema/description "whether to take number of lines from head or tail of log; 'all' takes all lines"

             :json-schema/order 23)))


(s/def ::lines
  (-> (st/spec pos-int?)
      (assoc :name "lines"
             :json-schema/description "number of lines to include in the log"

             :json-schema/order 24)))


(s/def ::log-line
  (-> (st/spec string?)
      (assoc :name "lines"
             :json-schema/type "string"
             :json-schema/description "a single line from the log")))


(s/def ::log
  (-> (st/spec (s/coll-of ::log-line :type vector?))
      (assoc :name "log"
             :json-schema/type "array"
             :json-schema/description "contents of log"

             :json-schema/order 25
             :json-schema/indexed false)))


(def deployment-log-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::parent ::service]
                         :opt-un [::since ::last-timestamp ::head-or-tail ::lines ::log]}]))


(s/def ::schema (su/only-keys-maps deployment-log-keys-spec))
