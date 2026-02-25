(ns app.worker
  (:require [app.queue :as queue])
  (:gen-class))

(def running? (atom true))

(defn process-job [job]
  (let [sleep-ms (+ 5000 (rand-int 5000))]
    (println (str "Processing job " (:id job) " (type=" (:type job) "), sleeping " sleep-ms "ms"))
    (Thread/sleep sleep-ms)
    {:processed-at (System/currentTimeMillis)
     :duration-ms sleep-ms}))

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
  (worker-loop))
