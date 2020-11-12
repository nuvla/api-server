(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-amazonec2
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-amazonec2 :as service]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps service/credential-template-keys-spec
                     ps/credential-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::service/template]}))
