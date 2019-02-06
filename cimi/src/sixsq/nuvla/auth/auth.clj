(ns sixsq.nuvla.auth.auth
  (:refer-clojure :exclude [update])
  (:require
    [sixsq.nuvla.auth.internal :as ia]
    [sixsq.nuvla.auth.utils.http :as uh]))


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


