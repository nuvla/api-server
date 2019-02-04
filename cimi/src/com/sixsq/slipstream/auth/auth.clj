(ns com.sixsq.slipstream.auth.auth
  (:refer-clojure :exclude [update])
  (:require
    [com.sixsq.slipstream.auth.internal :as ia]
    [com.sixsq.slipstream.auth.utils.http :as uh]))


(defn dispatch-on-authn-method
  [request]
  (-> request
      (uh/param-value :authn-method)
      keyword
      (or :internal)))


(defmulti login dispatch-on-authn-method)


(defmethod login :internal
  [request]
  (ia/login request))


(defn logout
  [_]
  (ia/logout))


