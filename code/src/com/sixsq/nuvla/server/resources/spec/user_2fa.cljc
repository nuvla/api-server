(ns com.sixsq.nuvla.server.resources.spec.user-2fa
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [com.sixsq.nuvla.server.util.spec :as su]))

(s/def ::method #{auth-2fa/method-email
                  auth-2fa/method-totp})

(s/def ::enable-2fa-body-schema
  (su/only-keys :req-un [::method]))

