(ns app.queue
  (:require [app.redis :as redis]
            [taoensso.carmine :as car])
  (:import [java.util UUID]))

(def stream-key "job-stream")
(def group-name "workers")
(def consumer-name (str "worker-" (UUID/randomUUID)))

(defn ensure-consumer-group []
  (try
    (car/wcar redis/conn-opts
              (car/xgroup "CREATE" stream-key group-name "0" "MKSTREAM"))
    (catch Exception _)))

(defn enqueue-job [job-type payload]
  (let [job-id (str (UUID/randomUUID))
        job {:id job-id
             :type job-type
             :payload payload
             :status "queued"
             :created-at (System/currentTimeMillis)}]
    (car/wcar redis/conn-opts (car/xadd stream-key "*" "job" (pr-str job)))
    job))

(defn dequeue-job [timeout-sec]
  (let [result (car/wcar redis/conn-opts
                         (car/xreadgroup "GROUP" group-name consumer-name
                                         "COUNT" 1
                                         "BLOCK" (* timeout-sec 1000)
                                         "STREAMS" stream-key ">"))]
    (when (and result (seq result))
      (let [[_ entries] (first result)
            [entry-id fields] (first entries)
            job-str (second fields)]
        (assoc (read-string job-str) :stream-entry-id entry-id)))))

(defn ack-job [entry-id]
  (car/wcar redis/conn-opts
            (car/xack stream-key group-name entry-id)
            (car/xdel stream-key entry-id)))

(defn complete-job [job-id result]
  (let [data {:status "completed" :result result :completed-at (System/currentTimeMillis)}]
    (car/wcar redis/conn-opts
              (car/hset (str "job-results:" job-id) "data" (pr-str data))
              (car/expire (str "job-results:" job-id) 3600))))

(defn fail-job [job-id error]
  (let [data {:status "failed" :error error :failed-at (System/currentTimeMillis)}]
    (car/wcar redis/conn-opts
              (car/hset (str "job-results:" job-id) "data" (pr-str data))
              (car/expire (str "job-results:" job-id) 3600))))

(defn get-job-result [job-id]
  (let [raw (car/wcar redis/conn-opts (car/hget (str "job-results:" job-id) "data"))]
    (when raw
      (read-string raw))))

(defn queue-length []
  (car/wcar redis/conn-opts (car/xlen stream-key)))
