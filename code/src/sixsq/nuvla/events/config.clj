(ns sixsq.nuvla.events.config
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st]))

;;
;; Dispatch functions
;;

(defn resource-type-dispatch [resource-type]
  resource-type)


(defn event-type-dispatch [event-type]
  event-type)


(defn event-category-dispatch [event-category]
  event-category)


;;
;; Enabled/disabled events
;;

(defmulti events-enabled?
          "Returns true if events should be logged for the given resource-type, false otherwise."
          resource-type-dispatch)


;;
;; Supported events
;;


(defmulti supported-event-types
          "Returns a map where the keys are the event types supported by the resource type and
           the values are the configuration of the event types."
          resource-type-dispatch)


(defn get-event-config
  [resource-type event-type]
  (-> resource-type
      (supported-event-types)
      (get event-type)))


(defn event-supported?
  "Returns true if the event type is supported on the resource, false otherwise."
  [{:keys [event-type resource]}]
  (some? (get-event-config (:resource-type resource) event-type)))


;;
;; Event details
;;


(defmulti details-spec
          "Returns the spec for the details field for the given event type."
          event-type-dispatch)


(defmethod details-spec :default
  [_event-type]
  nil)


(defmulti category-default-details-spec
          "Returns the default spec for the details field for event types of the given category."
          event-category-dispatch)


(defmethod category-default-details-spec :default
  [_event-type]
  nil)


;; by default no details are allowed
(def ^:const no-details-spec (st/spec nil?))


(defn details-valid?
  "Returns true if the details are valid for the event type, false otherwise.
   The spec for the details if fetched by trying, in this order:
   - call to `details-spec` multi-fn to get the spec for the details of the specific event type;
   - call to `category-default-details-spec` multi-fn to get the default spec for the event category;
   - otherwise a 'no details allowed' spec is used."
  [{:keys [resource event-type details]}]
  (let [{:keys [category]} (get-event-config (:resource-type resource) event-type)
        details-spec (or (details-spec event-type)
                         (category-default-details-spec category)
                         no-details-spec)]
    (s/valid? details-spec details)))
