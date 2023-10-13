(ns sixsq.nuvla.server.resources.hook-performance-report
  "
Stripe oauth hook.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [taoensso.tufte :as tufte]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action "perf")

(defonce stats-accumulator (tufte/add-accumulating-handler! {:ns-pattern "*"}))

(def initialization-order 1)
(defn execute
  [request]
  (try
    (a/throw-not-admin-request nil request)
    (r/text-response
      (if-let [m (not-empty @stats-accumulator)]
        (tufte/format-grouped-pstats m)
        "Nothing (try visit the /sleep/... endpoints)"))
    (catch Exception e
      (or (ex-data e) (throw e)))))
