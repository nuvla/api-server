(ns sixsq.nuvla.server.resources.spec.data-object
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.data :as data]))


(s/def ::type ::core/identifier)


(s/def ::state #{"NEW" "UPLOADING" "READY"})


(def credential-id-regex #"^credential/.*$")


(s/def ::credential (s/and string? #(re-matches credential-id-regex %)))


(s/def ::object ::core/nonblank-string)


(s/def ::bucket ::core/nonblank-string)


(s/def ::content-type ::core/nonblank-string)


(s/def ::bytes nat-int?)


(s/def ::md5sum ::core/token)


(def data-object-template-regex #"^data-object-template/[a-z]+(-[a-z]+)*$")


(s/def ::href (s/and string? #(re-matches data-object-template-regex %)))


(def common-data-object-attrs {:req-un [::type
                                        ::state
                                        ::object
                                        ::bucket
                                        ::credential]
                               :opt-un [::data/content-type
                                        ::href
                                        ::data/bytes
                                        ::data/md5sum]})
