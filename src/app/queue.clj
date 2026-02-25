(ns app.queue
  (:require [app.redis :refer [wcar*]]
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
    (wcar* (car/lpush queue-key (pr-str job)))
    job))

(defn dequeue-job [timeout-sec]
  (let [result (wcar* (car/brpop queue-key timeout-sec))]
    (when result
      (read-string (second result)))))

(defn complete-job [job-id result]
  (let [data {:status "completed" :result result :completed-at (System/currentTimeMillis)}]
    (wcar*
     (car/hset (str "job-results:" job-id) "data" (pr-str data))
     (car/expire (str "job-results:" job-id) 3600))))

(defn fail-job [job-id error]
  (let [data {:status "failed" :error error :failed-at (System/currentTimeMillis)}]
    (wcar*
     (car/hset (str "job-results:" job-id) "data" (pr-str data))
     (car/expire (str "job-results:" job-id) 3600))))

(defn get-job-result [job-id]
  (let [raw (wcar* (car/hget (str "job-results:" job-id) "data"))]
    (when raw
      (read-string raw))))

(defn queue-length []
  (wcar* (car/llen queue-key)))
