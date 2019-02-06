(ns sixsq.nuvla.server.resources.spec.credential-cloud
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::disabledMonitoring boolean?)


(def credential-keys-spec (su/merge-keys-specs [cred/credential-keys-spec
                                                {:opt-un [::disabledMonitoring]}]))
