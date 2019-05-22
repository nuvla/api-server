(ns sixsq.nuvla.server.resources.spec.credential
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::subtype ::ct/subtype)


(s/def ::method ::ct/method)


(def credential-keys-spec (su/merge-keys-specs [common/common-attrs
                                                {:req-un [::subtype
                                                          ::method]}]))
