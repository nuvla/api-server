(ns com.sixsq.slipstream.db.duratom.loader
  (:refer-clojure :exclude [load])
  (:require
    [com.sixsq.slipstream.db.atom.binding :as atom]
    [duratom.core :as duratom]
    [environ.core :as env]))


(defn load
  "Creates an atom-based database that will be persisted to the local file
   system. The path used to store the data can be specified in the
   DURATOM_FILE_PATH environmental variable. This defaults to the value
   '/var/lib/slipstream/duratom' if not specified."
  []
  (let [file-path (env/env :duratom-file-path "/var/lib/slipstream/duratom")]
    (atom/->AtomBinding (duratom/duratom :local-file
                                         :file-path file-path
                                         :init {}))))
