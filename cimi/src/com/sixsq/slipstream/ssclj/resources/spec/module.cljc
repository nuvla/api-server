(ns com.sixsq.slipstream.ssclj.resources.spec.module
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


;; define schema for references to module resources
(def ^:const module-href-regex #"^module/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(s/def ::href (s/and string? #(re-matches module-href-regex %)))
(s/def ::link (s/keys :req-un [::href]))


(def ^:const path-regex #"^[a-zA-Z0-9][\w\.-]*(/[a-zA-Z0-9][\w\.-]*)*$")

(defn path? [v] (boolean (re-matches path-regex v)))

(defn parent-path? [v] (or (= "" v) (path? v)))

(s/def ::path (s/and string? path?))

(s/def ::parentPath (s/and string? parent-path?))

(s/def ::type #{"PROJECT" "IMAGE" "COMPONENT" "APPLICATION"})

(s/def ::versions (s/coll-of (s/nilable ::cimi-common/resource-link) :min-count 1))

(s/def ::logoURL ::cimi-core/nonblank-string)

;;
;; data management attributes
;;

(s/def ::dataAcceptContentTypes (s/nilable (s/coll-of ::cimi-core/mimetype :min-count 1 :kind vector?)))

(s/def ::dataAccessProtocols (s/nilable (s/coll-of ::cimi-core/token :min-count 1 :kind vector?)))

(def module-keys-spec (su/merge-keys-specs [c/common-attrs
                                            {:req-un [::path ::parentPath ::type]
                                             :opt-un [::logoURL ::versions
                                                      ::dataAcceptContentTypes
                                                      ::dataAccessProtocols]}]))

(s/def ::module (su/only-keys-maps module-keys-spec))
