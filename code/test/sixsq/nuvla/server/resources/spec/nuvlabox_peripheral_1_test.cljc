(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1 :as nb-peripheral-1]
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

                 :version       1
                 :parent        "nuvlabox/uuid"

                 :identifier    "046d:082d"
                 :available     true
                 :device-path   "/dev/bus/usb/001/001"
                 :interface     "USB"
                 :port          1
                 :vendor        "SixSq"
                 :product       "HD Pro Webcam C920"
                 :classes       ["AUDIO" "VIDEO"]
                 :raw-data-sample             "{\"datapoint\": 1, \"value\": 2}"
                 :local-data-gateway-endpoint "data-gateway/video/1"
                 :data-gateway-enabled        true
                 :serial-number               "123456"
                 :video-device                "/dev/video0"})


(deftest check-nuvlabox-peripheral

  (stu/is-valid ::nb-peripheral-1/schema peripheral)
  (stu/is-invalid ::nb-peripheral-1/schema (assoc peripheral :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :identifier :available :classes}]
    (stu/is-invalid ::nb-peripheral-1/schema (dissoc peripheral attr)))

  ;; optional
  (doseq [attr #{:device-path :interface :vendor :port :product :raw-data-sample :local-data-gateway-endpoint
                 :data-gateway-enabled :serial-number :video-device}]
    (stu/is-valid ::nb-peripheral-1/schema (dissoc peripheral attr))))
