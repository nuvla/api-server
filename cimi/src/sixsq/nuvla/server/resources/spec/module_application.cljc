(ns sixsq.nuvla.server.resources.spec.module-application
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::commit ::cimi-core/nonblank-string)
(s/def ::author ::cimi-core/nonblank-string)

(s/def ::multiplicity nat-int?)
(s/def ::maxProvisioningFailures nat-int?)

(s/def ::component ::module/link)

(s/def ::mapped boolean?)
(s/def ::value string?)

(s/def ::parameterMapping (su/only-keys :req-un [::module-component/parameter ::mapped ::value]))

(s/def ::parameterMappings (s/coll-of ::parameterMapping :min-count 1 :kind vector?))

(def ^:const node-name-regex #"^[a-zA-Z0-9]+(_[a-zA-Z0-9]+)*$")
(s/def ::node (s/and string? #(re-matches node-name-regex %)))

(s/def ::node-map (su/only-keys :req-un [::node ::multiplicity ::component]
                                :opt-un [::maxProvisioningFailures
                                         ::parameterMappings]))

(s/def ::nodes (s/coll-of ::node-map :min-count 1 :kind vector?))

(def module-application-keys-spec (su/merge-keys-specs [c/common-attrs
                                                        {:req-un [::author]
                                                         :opt-un [::nodes
                                                                  ::commit]}]))

(s/def ::module-application (su/only-keys-maps module-application-keys-spec))
