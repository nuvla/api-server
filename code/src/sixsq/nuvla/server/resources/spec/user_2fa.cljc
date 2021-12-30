(ns sixsq.nuvla.server.resources.spec.user-2fa
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::method #{"email"})

(s/def ::enable-2fa-body-schema
  {:req-un [::method
            ::redirect-url]})

(s/def ::disable-2fa-body-schema
  {:req-un [::redirect-url]})

