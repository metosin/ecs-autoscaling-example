(ns app.redis
  (:require [app.config :as config]))

(def conn-opts
  {:pool {}
   :spec {:host (:redis-host config/config)
          :port (:redis-port config/config)}})
