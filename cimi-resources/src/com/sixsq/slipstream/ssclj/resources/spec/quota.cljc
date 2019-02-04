(ns com.sixsq.slipstream.ssclj.resources.spec.quota
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.db.filter.parser :as parser]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
    [instaparse.core :as insta]))


(defn valid-cimi-filter?
  [f]
  (try
    (not (insta/failure? (parser/parse-cimi-filter f)))
    (catch Throwable _ false)))


(s/def ::resource ::cimi-core/nonblank-string)
(s/def ::selection (s/and string? valid-cimi-filter?))
(s/def ::aggregation ::cimi-core/nonblank-string)
(s/def ::limit nat-int?)
(s/def ::organization ::cimi-core/nonblank-string)


(s/def ::quota
  (su/only-keys-maps c/common-attrs
                     {:req-un [::resource ::selection ::aggregation ::limit]
                      :opt-un [::organization]}))
