(ns sixsq.nuvla.server.util.multimethods)

(defn implemented?
  "Returns true if the multimethod has been implemented for precisely
   the given dispatch value"
  [multifn dispatch-val]
  (contains? (methods multifn) dispatch-val))
