(ns sixsq.nuvla.server.resources.user-interface
  "
User interface contain multimethods of user resource. Interface is there to resolve ns load order.
"
  (:require [sixsq.nuvla.server.util.log :as logu]))

(defn dispatch-on-registration-method [resource]
  (get-in resource [:template :method]))

(defmulti create-validate-subtype dispatch-on-registration-method)


(defmethod create-validate-subtype :default
  [resource]
  (let [method (dispatch-on-registration-method resource)]
    (logu/log-and-throw-400 (format "invalid user registration method '%s'" method))))


;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:method resource))

;; transforms the user template into a user resource
;;
;; The concrete implementation of this method MUST return a two-element
;; tuple containing a response fragment and the created user resource.
;; The response fragment will be merged with the 'add-impl' function
;; response and should be used to override the return status (e.g. to
;; instead provide a redirect) and to set a cookie header.
;;
(defmulti tpl->user dispatch-conversion)

; All concrete session types MUST provide an implementation of this
;; multimethod. The default implementation will throw an 'internal
;;; server error' exception.
;;
(defmethod tpl->user :default
  [resource request]
  [{:status 400, :message "missing or invalid user-template reference"} nil])


;; handles any actions that must be taken after the user is added
(defmulti post-user-add dispatch-conversion)

;; default implementation is a no-op
(defmethod post-user-add :default
  [resource request]
  nil)
