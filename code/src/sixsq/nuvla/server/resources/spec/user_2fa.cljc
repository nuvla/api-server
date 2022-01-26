(ns sixsq.nuvla.server.resources.spec.user-2fa
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::method #{user-utils/method-email
                  user-utils/method-totp})

(s/def ::enable-2fa-body-schema
  (su/only-keys :req-un [::method]))

