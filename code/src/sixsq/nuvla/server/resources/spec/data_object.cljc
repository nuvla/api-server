(ns sixsq.nuvla.server.resources.spec.data-object
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]))


(s/def ::type ::cimi-core/identifier)


(s/def ::state #{"NEW" "UPLOADING" "READY"})


(def credential-id-regex #"^credential/.*$")


(s/def ::credential (s/and string? #(re-matches credential-id-regex %)))


(s/def ::object ::cimi-core/nonblank-string)


(s/def ::bucket ::cimi-core/nonblank-string)


(s/def ::content-type ::cimi-core/nonblank-string)


(s/def ::size nat-int?)


(s/def ::md5sum ::cimi-core/token)


(def data-object-template-regex #"^data-object-template/[a-z]+(-[a-z]+)*$")


(s/def ::href (s/and string? #(re-matches data-object-template-regex %)))


(def common-data-object-attrs {:req-un [::type
                                        ::state
                                        ::object
                                        ::bucket
                                        ::credential]
                               :opt-un [::content-type
                                        ::href
                                        ::size
                                        ::md5sum]})
