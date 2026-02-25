(ns app.config)

(def config
  {:redis-host (or (System/getenv "REDIS_HOST") "localhost")
   :redis-port (parse-long (or (System/getenv "REDIS_PORT") "6379"))
   :web-port   (parse-long (or (System/getenv "PORT") "8080"))
   :aws-region (or (System/getenv "AWS_REGION") "ap-northeast-1")
   :cw-namespace (or (System/getenv "CW_NAMESPACE") "ECSAutoscaling")
   :ecs-cluster  (or (System/getenv "ECS_CLUSTER") "autoscaling-example")
   :ecs-service  (or (System/getenv "ECS_SERVICE") "worker")})
