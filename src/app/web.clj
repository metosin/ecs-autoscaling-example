(ns app.web
  (:require [app.config :as config]
            [app.queue :as queue]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]])
  (:gen-class))

(defn handler [{:keys [request-method uri body]}]
  (cond
    (and (= request-method :post) (= uri "/jobs"))
    (let [{:strs [type payload]} body
          job (queue/enqueue-job (or type "default") (or payload {}))]
      {:status 201
       :body {:job-id (:id job) :status "queued"}})

    (and (= request-method :get) (.startsWith uri "/jobs/"))
    (let [job-id (subs uri 6)
          result (queue/get-job-result job-id)]
      (if result
        {:status 200 :body result}
        {:status 202 :body {:status "pending"}}))

    (and (= request-method :get) (= uri "/health"))
    {:status 200 :body {:status "ok"}}

    (and (= request-method :get) (= uri "/queue-length"))
    {:status 200 :body {:queue-length (queue/queue-length)}}

    :else
    {:status 404 :body {:error "not found"}}))

(def app
  (-> handler
      (wrap-json-body)
      (wrap-json-response)))

(defn -main [& _args]
  (let [port (:web-port config/config)]
    (println (str "Starting web server on port " port))
    (jetty/run-jetty app {:port port :join? true})))
