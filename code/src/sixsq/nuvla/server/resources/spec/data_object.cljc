(ns sixsq.nuvla.server.resources.spec.data-object
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]))


(s/def ::object-type ::cimi-core/identifier)


(s/def ::state #{"new" "ready"})


(def credential-id-regex #"^credential/.*$")


(s/def ::credential (s/and string? #(re-matches credential-id-regex %)))


(s/def ::object-name ::cimi-core/nonblank-string)


(s/def ::bucket-name ::cimi-core/nonblank-string)


(s/def ::content-type ::cimi-core/nonblank-string)


(s/def ::size nat-int?)


(s/def ::md5sum ::cimi-core/token)


(def data-object-template-regex #"^data-object-template/[a-z]+(-[a-z]+)*$")


(s/def ::href (s/and string? #(re-matches data-object-template-regex %)))


(def common-data-object-attrs {:req-un [::object-type
                                        ::state
                                        ::object-name
                                        ::bucket-name
                                        ::credential]
                               :opt-un [::content-type
                                        ::href
                                        ::size
                                        ::md5sum]})
