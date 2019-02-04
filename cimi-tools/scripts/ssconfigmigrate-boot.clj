#!/usr/bin/env boot

;; Should be run from root of the project with build.boot.

(load-file "build.boot")

(require '[com.sixsq.slipstream.tools.cli.ssconfigmigrate :as d])

(defn -main
  [& args]
  (apply d/-main args))

