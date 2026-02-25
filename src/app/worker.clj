(ns app.worker
  (:require [app.config :as config]
            [app.queue :as queue]
            [cognitect.aws.client.api :as aws])
  (:gen-class))

(def running? (atom true))

(defn process-job [job]
  (let [sleep-ms (+ 5000 (rand-int 5000))]
    (println (str "Processing job " (:id job) " (type=" (:type job) "), sleeping " sleep-ms "ms"))
    (Thread/sleep sleep-ms)
    {:processed-at (System/currentTimeMillis)
     :duration-ms sleep-ms}))

(defn publish-queue-metric [cw-client]
  (let [length (queue/queue-length)]
    (println (str "Publishing RedisQueueLength=" length " to CloudWatch"))
    (try
      (aws/invoke cw-client
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

(defn metric-publisher-loop [cw-client]
  (while @running?
    (try
      (publish-queue-metric cw-client)
      (catch Exception e
        (println (str "Metric publishing error: " (.getMessage e)))))
    (let [deadline (+ (System/currentTimeMillis) 60000)]
      (while (and @running? (< (System/currentTimeMillis) deadline))
        (Thread/sleep 1000)))))

(defn worker-loop []
  (println "Worker loop started")
  (while @running?
    (try
      (when-let [job (queue/dequeue-job 30)]
        (try
          (let [result (process-job job)]
            (queue/complete-job (:id job) result))
          (catch Exception e
            (println (str "Job " (:id job) " failed: " (.getMessage e)))
            (queue/fail-job (:id job) (.getMessage e)))))
      (catch Exception e
        (println (str "Worker error: " (.getMessage e)))
        (Thread/sleep 1000)))))

(defn -main [& _args]
  (println "Starting worker")
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (println "Shutting down worker...")
                               (reset! running? false)
                               (println "Worker stopped"))))
  (let [cw-client (aws/client {:api :monitoring
                               :region (:aws-region config/config)})]
    (future (metric-publisher-loop cw-client))
    (worker-loop)))
