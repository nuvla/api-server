(ns sixsq.nuvla.server.resources.spec.container-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.container :as container]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(deftest check-image
  (let [image {:image-name "ubuntu"
               :tag        "latest"
               :repository "debian"
               :registry   "registry.nuvla.org:5000"}]

    (stu/is-valid ::container/image image)

    (stu/is-invalid ::container/image (assoc image :bad "value"))

    ; required
    (doseq [attr #{:image-name}]
      (stu/is-invalid ::container/image (dissoc image attr)))

    ;optional
    (doseq [attr #{:tag :repository :registry}]
      (stu/is-valid ::container/image (dissoc image attr)))))


(deftest check-mount
  (let [mount {:mount-type "bind"
               :source     "/abc/file"
               :target     "/file"
               :read-only  false}]

    (stu/is-valid ::container/mount mount)

    (stu/is-invalid ::container/mount (assoc mount :bad "value"))

    ; required
    (doseq [attr #{:mount-type :target}]
      (stu/is-invalid ::container/mount (dissoc mount attr)))

    ;optional
    (doseq [attr #{:read-only :source}]
      (stu/is-valid ::container/mount (dissoc mount attr)))))


(deftest check-port
  (let [port {:target-port    22
              :published-port 2222
              :protocol       "tcp"}]

    (stu/is-valid ::container/port port)

    (stu/is-invalid ::container/port (assoc port :bad "value"))

    ; required
    (doseq [attr #{:target-port}]
      (stu/is-invalid ::container/port (dissoc port attr)))

    ;optional
    (doseq [attr #{:published-port :protocol}]
      (stu/is-valid ::container/port (dissoc port attr)))))

(deftest check-volume-option
  (let [volume-option {:key1 "value 1"}]

    (stu/is-valid ::container/volume-options volume-option)

    (stu/is-invalid ::container/volume-options (assoc volume-option :bad ["value"]))))
