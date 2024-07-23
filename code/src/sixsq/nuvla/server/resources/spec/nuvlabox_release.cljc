(ns sixsq.nuvla.server.resources.spec.nuvlabox-release
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::release (-> (st/spec ::core/token)
                     (assoc :name "release"
                            :json-schema/type "string"
                            :json-schema/description "nuvlabox release version"
                            :json-schema/group "body"
                            :json-schema/order 18)))

(s/def ::url (-> (st/spec ::core/url)
                 (assoc :name "url"
                        :json-schema/type "uri"
                        :json-schema/description "nuvlabox release url"
                        :json-schema/group "body"
                        :json-schema/order 19)))

(s/def ::pre-release (-> (st/spec boolean?)
                         (assoc :name "pre-release"
                                :json-schema/type "boolean"
                                :json-schema/description "is it a pre-release?"
                                :json-schema/group "body"
                                :json-schema/order 20)))


(s/def ::release-date (-> (st/spec ::core/timestamp)
                          (assoc :name "release date"
                                 :json-schema/type "date-time"
                                 :json-schema/description "the date when the release was published"
                                 :json-schema/group "body"
                                 :json-schema/order 21)))

(s/def ::release-notes (-> (st/spec string?)
                           (assoc :name "release notes"
                                  :json-schema/type "string"
                                  :json-schema/description "descriptive info about the release"
                                  :json-schema/group "body"
                                  :json-schema/order 22)))

(s/def ::file (-> (st/spec ::core/nonblank-string)
                  (assoc :name "compose file"
                         :json-schema/type "string"
                         :json-schema/description "raw compose file"
                         :json-schema/order 23)))

(s/def ::name (-> (st/spec ::core/nonblank-string)
                  (assoc :name "compose file name"
                         :json-schema/type "string"
                         :json-schema/description "name of the compose file"
                         :json-schema/order 24)))

(s/def ::scope (-> (st/spec string?)
                   (assoc :name "compose file scope"
                          :json-schema/type "string"
                          :json-schema/description "type of compose file (core, usb, modbus, etc.)"
                          :json-schema/order 25)))

(s/def ::compose-file-info (-> (st/spec (su/only-keys :req-un [::file ::scope ::name]))
                               (assoc :name "compose file object"
                                      :json-schema/type "map"
                                      :json-schema/description "structure of the compose file"
                                      :json-schema/group "body"
                                      :json-schema/order 26)))

(s/def ::compose-files (-> (st/spec (s/coll-of ::compose-file-info :min-count 1 :kind vector?))
                           (assoc :name "compose files"
                                  :json-schema/type "array"
                                  :json-schema/description "all compose files for this nuvlabox release"
                                  :json-schema/group "body"
                                  :json-schema/order 27)))

(s/def ::published
  (-> (st/spec boolean?)
      (assoc :name "published"
             :json-schema/type "boolean"
             :json-schema/description "nuvlabox release is published")))

(s/def ::implementation (-> (st/spec #{"python" "go"})
                            (assoc :name "implementation"
                                   :json-schema/type "string"
                                   :json-schema/description "implementation technology (python, go)")))

;;
;; -------
;;

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::release
                               ::url
                               ::pre-release
                               ::release-date
                               ::compose-files]
                      :opt-un [::release-notes
                               ::published
                               ::implementation]}))
