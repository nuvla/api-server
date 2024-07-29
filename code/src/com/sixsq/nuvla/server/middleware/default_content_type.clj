(ns com.sixsq.nuvla.server.middleware.default-content-type
  "Ring middleware that adds a default content type to the request, only if it
   isn't already present and there is a body.")


(defn default-content-type [wrapped-handler default]
  (fn [{{:strs [content-type]} :headers body :body :as request}]
    (let [updated-request (if (and body (nil? content-type))
                            (assoc-in request [:headers "content-type"] default)
                            request)]
      (wrapped-handler updated-request))))
