(ns com.sixsq.nuvla.server.resources.spec.module-component
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.container :as container]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [com.sixsq.nuvla.server.util.spec :as su]
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

;; list from https://github.com/containerd/containerd/blob/1ac546b3c4a3331a9997427052d1cb9888a2f3ef/platforms/database.go#L63
;; and from https://github.com/moby/moby/blob/2d32d7504104e88ad8c40d843be1e3d75cdfec0e/profiles/seccomp/seccomp_linux.go#L29-L64
;; arm variants added
(def valid-architectures ["386" "x86" "x86_64" "amd64" "amd64p32" "aarch64"
                          "arm" "armbe" "arm64" "arm64/v8" "arm64be"
                          "arm/v5" "arm/v6" "arm/v7" "armv7l"
                          "mips" "mipsel" "mipsel64" "mipsle" "mips64" "mips64le" "mips64n32" "mips64p32" "mips64p32le"
                          "mips3l64n32"
                          "ppc" "ppc64" "ppc64le"
                          "riscv64" "s390" "s390x" "sparc" "sparc64"])

(s/def ::architecture
  (-> (st/spec (s/and string? (set valid-architectures)))
      (assoc :name "architecture"
             :json-schema/type "string"
             :json-schema/description "CPU architecture"

             :json-schema/value-scope {:values  valid-architectures
                                       :default "amd64"})))


(s/def ::architectures
  (-> (st/spec (s/coll-of ::architecture :min-count 1))
      (assoc :name "architectures"
             :json-schema/type "array"
             :json-schema/description "component CPU architectures"

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


(s/def ::min-cpu
  (-> (st/spec (s/and number? pos?))
      (assoc :name "min-cpu"
             :json-schema/type "double"
             :json-schema/description "minimum required CPUs")))

(s/def ::min-ram
  (-> (st/spec pos-int?)
      (assoc :name "min-ram"
             :json-schema/type "integer"
             :json-schema/description "minimum required memory in MiB")))

(s/def ::min-disk
  (-> (st/spec pos-int?)
      (assoc :name "min-disk"
             :json-schema/type "integer"
             :json-schema/description "minimum required disk in MiB")))

(s/def ::minimum-requirements
  (-> (st/spec (su/only-keys
                 :opt-un [::min-cpu
                          ::min-ram
                          ::min-disk]))
      (assoc :name "minimum-requirements"
             :json-schema/type "map")))

(def module-component-keys-spec (su/merge-keys-specs [common/common-attrs
                                                      {:req-un [::author
                                                                ::architectures
                                                                ::container/image]
                                                       :opt-un [::commit
                                                                ::container/memory
                                                                ::container/cpus
                                                                ::container/restart-policy
                                                                ::container/ports
                                                                ::container/mounts
                                                                ::container/private-registries
                                                                ::deployment/registries-credentials
                                                                ::urls
                                                                ::container/environmental-variables
                                                                ::output-parameters
                                                                ::minimum-requirements]}]))


(s/def ::schema (su/only-keys-maps module-component-keys-spec))
