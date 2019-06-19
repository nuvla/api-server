(ns sixsq.nuvla.server.resources.spec.deployment-parameter
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


(s/def ::node-id ::core/token)

(s/def ::name ::core/token)

(s/def ::value ::core/nonblank-string)

(def deployment-parameter-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::parent ::name]
                         :opt-un [::node-id ::value]}]))

(s/def ::deployment-parameter (su/only-keys-maps deployment-parameter-keys-spec))
