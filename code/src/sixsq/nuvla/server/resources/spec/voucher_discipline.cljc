(ns sixsq.nuvla.server.resources.spec.voucher-discipline
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema
  (su/only-keys-maps common/common-attrs))