(ns sixsq.nuvla.server.resources.spec.module-component
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.container :as container]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::author
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "author"
             :json-schema/description "author of module"

             :json-schema/order 30)))


(s/def ::commit
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "commit"
             :json-schema/description "commit message"

             :json-schema/order 31)))


(s/def ::architecture
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "architecture"
             :json-schema/description "component CPU architecture"

             :json-schema/order 32)))


(s/def ::url-tuple
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 2 :max-count 2))
      (assoc :name "url-tuple"
             :json-schema/type "array"
             :json-schema/display-name "URL tuple"
             :json-schema/description "tuple of the URL label and pattern, e.g. 'https://${host}:${port-443}/some/path'")))


(s/def ::urls
  (-> (st/spec (s/coll-of ::url-tuple :min-count 1 :kind vector?))
      (assoc :name "urls"
             :json-schema/type "array"
             :json-schema/description "tuple of the URL name and pattern"
             :json-schema/order 33)))


(s/def ::name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "name"
             :json-schema/description "parameter name")))


(s/def ::description
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "description"
             :json-schema/description "parameter description")))


(s/def ::parameter
  (-> (st/spec (su/only-keys :req-un [::name ::description]))
      (assoc :name "parameter"
             :json-schema/type "map"
             :json-schema/description "parameter name and description"
             :json-schema/order 33)))


(s/def ::output-parameters
  (-> (st/spec (s/coll-of ::parameter :kind vector?))
      (assoc :name "output-parameters"
             :json-schema/type "array"
             :json-schema/display-name "output parameters"
             :json-schema/description "list of output parameters"
             :json-schema/order 34)))


(def module-component-keys-spec (su/merge-keys-specs [common/common-attrs
                                                      {:req-un [::author
                                                                ::architecture
                                                                ::container/image]
                                                       :opt-un [::commit
                                                                ::container/ports
                                                                ::container/mounts
                                                                ::urls
                                                                ::output-parameters]}]))


(s/def ::schema (su/only-keys-maps module-component-keys-spec))
