(ns app.redis
  (:require [app.config :as config]
            [taoensso.carmine :as car]))

(def conn-opts
  {:pool {}
   :spec {:host (:redis-host config/config)
          :port (:redis-port config/config)}})

(defmacro wcar* [& body]
  `(car/wcar conn-opts ~@body))
