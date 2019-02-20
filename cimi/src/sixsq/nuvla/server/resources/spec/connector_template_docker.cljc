(ns sixsq.nuvla.server.resources.spec.connector-template-docker
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.connector-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]))

(def keys-spec {:req-un [::ct/endpoint
                         ::ct/updateClientURL]})

(def opt-keys-spec {:opt-un (concat (:req-un keys-spec) (:opt-un keys-spec))})

;; Defines the contents of the docker connector template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec keys-spec))

;; Defines the contents of the docker connector template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec opt-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:opt-un [::template]}))