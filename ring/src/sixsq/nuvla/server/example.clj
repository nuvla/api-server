(ns sixsq.nuvla.server.example
  "Provides a simple ring example to ensure that generic ring container works.")


(defn handler [request]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Ring Example Running!\n"})


(defn init []
  [handler nil])
