(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-0-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-0 :as nb-peripheral-0]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def peripheral {:id            (str nb-peripheral/resource-type "/uuid")
                 :resource-type nb-peripheral/resource-type
                 :name          "Webcam C920"
                 :description   "Logitech, Inc. HD Pro Webcam C920"
                 :created       timestamp
                 :updated       timestamp

                 :acl           valid-acl

                 :version       0
                 :parent        "nuvlabox/uuid"

                 :identifier    "046d:082d"
                 :available     true
                 :device-path   "/dev/bus/usb/001/001"
                 :interface     "USB"})


(deftest check-nuvlabox-peripheral

  (stu/is-valid ::nb-peripheral-0/schema peripheral)
  (stu/is-invalid ::nb-peripheral-0/schema (assoc peripheral :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :identifier :available}]
    (stu/is-invalid ::nb-peripheral-0/schema (dissoc peripheral attr)))

  ;; optional
  (doseq [attr #{:device-path :interface}]
    (stu/is-valid ::nb-peripheral-0/schema (dissoc peripheral attr))))
