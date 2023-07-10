(ns sixsq.nuvla.server.resources.spec.api-docs
  (:require
    [clojure.spec.alpha :as s]
    [spec-tools.openapi.spec :as openapi-spec]))

(s/def ::schema ::openapi-spec/openapi)

