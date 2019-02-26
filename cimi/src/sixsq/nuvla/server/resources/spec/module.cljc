(ns sixsq.nuvla.server.resources.spec.module
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


;; define schema for references to module resources
(def ^:const module-href-regex #"^module/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::href (s/and string? #(re-matches module-href-regex %)))


(s/def ::link (s/keys :req-un [::href]))


(def ^:const path-regex #"^[a-zA-Z0-9][\w\.-]*(/[a-zA-Z0-9][\w\.-]*)*$")


(defn path? [v] (boolean (re-matches path-regex v)))


(defn parent-path? [v] (or (= "" v) (path? v)))


(s/def ::path (s/and string? path?))


(s/def ::parent-path (s/and string? parent-path?))


(s/def ::type #{"PROJECT" "IMAGE"})


(s/def ::versions (s/coll-of (s/nilable ::cimi-common/resource-link) :min-count 1))


(s/def ::logo-url ::cimi-core/nonblank-string)

;;
;; data management attributes
;;

(s/def ::data-accept-content-types (s/coll-of ::cimi-core/mimetype :kind vector?))

(s/def ::data-access-protocols (s/coll-of ::cimi-core/token :kind vector?))

(def module-keys-spec (su/merge-keys-specs [c/common-attrs
                                            {:req-un [::path
                                                      ::parent-path
                                                      ::type
                                                      ::data-accept-content-types
                                                      ::data-access-protocols]
                                             :opt-un [::logo-url
                                                      ::versions]}]))

(s/def ::module (su/only-keys-maps module-keys-spec))
