(ns sixsq.nuvla.server.resources.spec.credential
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const credential-id-regex #"^credential/[0-9a-f]+(-[0-9a-f]+)*$")


(def credential-id-spec (-> (st/spec (s/and string? #(re-matches credential-id-regex %)))
                            (assoc :name "credential-id"
                                   :json-schema/type "resource-id"
                                   :json-schema/description "reference to credential resource")))


(s/def ::subtype ::ct/subtype)


(s/def ::method ::ct/method)


(def credential-keys-spec (su/merge-keys-specs [common/common-attrs
                                                {:req-un [::subtype
                                                          ::method]}]))
