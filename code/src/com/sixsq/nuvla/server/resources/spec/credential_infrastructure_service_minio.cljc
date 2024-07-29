(ns com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service-minio
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-minio :as service]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps service/credential-template-keys-spec-req
                     cred-infra-service/credential-service-keys-spec
                     ps/credential-keys-spec))
