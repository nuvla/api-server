(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-2-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-2 :as nb-peripheral-2]
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

                 :version       2
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
                 :video-device                "/dev/video0"
                 :additional-assets           {:devices     ["/dev/device1", "/dev/device2"]
                                               :libraries   ["/lib/a", "/lib/b"]}
                 :resources     [{:unit "cuda cores"
                                  :capacity "100"
                                  :load 50}
                                 {:unit "memory"
                                  :capacity "1024"
                                  :load 20}]})


(deftest check-nuvlabox-peripheral

  (stu/is-valid ::nb-peripheral-2/schema peripheral)
  (stu/is-invalid ::nb-peripheral-2/schema (assoc peripheral :bad-attr "BAD_ATTR"))

  ;; required
  (doseq [attr #{:id :resource-type :created :updated :acl
                 :version :parent :identifier :available :classes}]
    (stu/is-invalid ::nb-peripheral-2/schema (dissoc peripheral attr)))

  ;; optional
  (doseq [attr #{:device-path :interface :vendor :port :product :raw-data-sample :local-data-gateway-endpoint
                 :data-gateway-enabled :serial-number :video-device :additional-assets :resources}]
    (stu/is-valid ::nb-peripheral-2/schema (dissoc peripheral attr))))
