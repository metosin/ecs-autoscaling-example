(ns app.queue
  (:require [app.redis :as redis]
            [taoensso.carmine :as car])
  (:import [java.util UUID]))

(def queue-key "job-queue")

(defn enqueue-job [job-type payload]
  (let [job-id (str (UUID/randomUUID))
        job {:id job-id
             :type job-type
             :payload payload
             :status "queued"
             :created-at (System/currentTimeMillis)}]
    (car/wcar redis/conn-opts (car/lpush queue-key (pr-str job)))
    job))

(defn dequeue-job [timeout-sec]
  (let [result (car/wcar redis/conn-opts (car/brpop queue-key timeout-sec))]
    (when result
      (read-string (second result)))))

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
  (car/wcar redis/conn-opts (car/llen queue-key)))
