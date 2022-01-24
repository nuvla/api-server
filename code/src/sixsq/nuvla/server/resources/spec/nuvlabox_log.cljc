(ns sixsq.nuvla.server.resources.spec.nuvlabox-log
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const nuvlabox-id-regex #"^nuvlabox/[0-9a-f]+(-[0-9a-f]+)*$")


(s/def ::parent (-> (st/spec (s/and string? #(re-matches nuvlabox-id-regex %)))
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/description "reference to parent nuvlabox resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


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


(s/def ::lines
  (-> (st/spec pos-int?)
      (assoc :name "lines"
             :json-schema/description "number of lines to include in the log"

             :json-schema/order 23)))


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

             :json-schema/order 24
             :json-schema/indexed false)))


(s/def ::components
  (-> (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    (assoc :name "components-names"
      :json-schema/display-name "components names"
      :json-schema/description "names of the NuvlaBox componets"

      :json-schema/editable false
      :json-schema/order 21)))


(def nuvlabox-log-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::parent ::components]
                         :opt-un [::since ::last-timestamp ::lines ::log]}]))


(s/def ::schema (su/only-keys-maps nuvlabox-log-keys-spec))
