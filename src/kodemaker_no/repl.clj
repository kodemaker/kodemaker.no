(ns kodemaker-no.repl
  (require
     [kodemaker-no.web :refer :all]
     ring.adapter.jetty))



(defonce server (ring.adapter.jetty/run-jetty #'app {:port 3000 :join? false}))
(defn start-server []
  (.start server))

(defn stop-server []
  (.stop server))
