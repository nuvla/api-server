(ns com.sixsq.nuvla.server.resources.spec.deployment-parameter
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const deployment-id-regex #"^deployment/[0-9a-f]+(-[0-9a-f]+)*$")


(s/def ::parent (-> (st/spec (s/and string? #(re-matches deployment-id-regex %)))
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/description "reference to parent deployment resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


(s/def ::name (-> (st/spec ::core/token)
                  (assoc :name "name"
                         :json-schema/description "name of the deployment parameter"

                         :json-schema/section "meta"
                         :json-schema/editable false
                         :json-schema/order 7)))


(s/def ::node-id
  (-> (st/spec ::core/token)
      (assoc :name "node-id"
             :json-schema/display-name "node ID"
             :json-schema/description "node identifier"

             :json-schema/editable false
             :json-schema/order 20)))


(s/def ::value
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "value"
             :json-schema/description "value of the deployment parameter"

             :json-schema/order 21)))


(def deployment-parameter-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::parent ::name]
                         :opt-un [::node-id ::value]}]))

(s/def ::deployment-parameter (su/only-keys-maps deployment-parameter-keys-spec))
