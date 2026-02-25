(ns app.web
  (:require [app.config :as config]
            [app.queue :as queue]
            [cognitect.aws.client.api :as aws]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]])
  (:gen-class))

(defonce cw-client
  (delay (aws/client {:api :monitoring
                      :region (:aws-region config/config)})))

(defn publish-queue-metric []
  (let [length (queue/queue-length)]
    (println (str "Publishing RedisQueueLength=" length " to CloudWatch"))
    (try
      (aws/invoke @cw-client
                  {:op :PutMetricData
                   :request {:Namespace (:cw-namespace config/config)
                             :MetricData [{:MetricName "RedisQueueLength"
                                           :Value (double length)
                                           :Unit "Count"
                                           :Dimensions [{:Name "ClusterName"
                                                         :Value (:ecs-cluster config/config)}
                                                        {:Name "ServiceName"
                                                         :Value (:ecs-service config/config)}]}]}})
      (catch Exception e
        (println (str "Failed to publish metric: " (.getMessage e)))))))

(def running? (atom true))

(defn metric-publisher-loop []
  (while @running?
    (try
      (publish-queue-metric)
      (catch Exception e
        (println (str "Metric publishing error: " (.getMessage e)))))
    (let [deadline (+ (System/currentTimeMillis) 15000)]
      (while (and @running? (< (System/currentTimeMillis) deadline))
        (Thread/sleep 1000)))))

(defn handler [{:keys [request-method uri body]}]
  (cond
    (and (= request-method :post) (= uri "/jobs"))
    (let [{:strs [type payload]} body
          job (queue/enqueue-job (or type "default") (or payload {}))]
      (future (publish-queue-metric))
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
    (future (metric-publisher-loop))
    (let [server (jetty/run-jetty app {:port port :join? false})]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn []
                                   (println "Shutting down web server...")
                                   (reset! running? false)
                                   (.stop server)
                                   (println "Web server stopped"))))
      (.join server))))
