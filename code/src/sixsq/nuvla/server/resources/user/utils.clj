(ns sixsq.nuvla.server.resources.user.utils
  (:require
    [sixsq.nuvla.server.util.response :as r]))


(def min-password-length 8)


(defn check-password-constraints
  [{:keys [password passwordRepeat]}]
  (cond
    (not (and password passwordRepeat)) (throw (r/ex-bad-request "both password fields must be specified"))
    (not= password passwordRepeat) (throw (r/ex-bad-request "password fields must be identical"))
    (< (count password) min-password-length) (throw (r/ex-bad-request (str "password must have at least " min-password-length " characters"))))
  true)
