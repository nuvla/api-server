(ns sixsq.nuvla.server.resources.spec.nuvlabox-state
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))

;;
;; general information
;;

(s/def ::next-heartbeat ::core/timestamp)

(s/def ::state #{"NEW" "ONLINE" "OFFLINE" "UNKNOWN"})


;;
;; resource information
;;

(s/def ::capacity pos-int?)

(s/def ::load (s/and double? #(not (neg? %))))

(s/def ::cpu (su/only-keys :req-un [::capacity ::load]))


(s/def ::used nat-int?)

(s/def ::ram (su/only-keys :req-un [::capacity ::used]))


(s/def ::device ::core/nonblank-string)

(s/def ::disk-info (su/only-keys :req-un [::device ::capacity ::used]))

(s/def ::disks (s/coll-of ::disk-info :min-count 1 :kind vector?))

(s/def ::resources (su/only-keys :req-un [::cpu ::ram ::disks]))

;;
;; peripherals
;;

(s/def ::busy boolean?)

(s/def ::vendor-id ::core/nonblank-string)

(s/def ::device-id ::core/nonblank-string)

(s/def ::bus-id ::core/nonblank-string)

(s/def ::product-id ::core/nonblank-string)

(s/def ::description string?)

(s/def ::usb-info (su/only-keys :req-un [::busy
                                         ::vendor-id
                                         ::device-id
                                         ::bus-id
                                         ::product-id
                                         ::description]))

(s/def ::usb (s/coll-of ::usb-info :kind vector?))

(s/def ::peripherals (su/only-keys :opt-un [::usb]))


;;
;; miscellaneous
;;

(s/def ::wifi-password ::core/nonblank-string)


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::common/parent              ;; reference to nuvlabox-record
                               ::state]
                      :opt-un [::next-heartbeat
                               ::resources
                               ::peripherals
                               ::wifi-password]}))
