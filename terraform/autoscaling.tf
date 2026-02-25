resource "aws_appautoscaling_target" "worker" {
  max_capacity       = var.worker_max_capacity
  min_capacity       = var.worker_min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.worker.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "worker_queue_depth" {
  name               = "${var.project_name}-worker-queue-depth"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.worker.resource_id
  scalable_dimension = aws_appautoscaling_target.worker.scalable_dimension
  service_namespace  = aws_appautoscaling_target.worker.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_target_value
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    customized_metric_specification {
      metrics {
        id          = "m1"
        label       = "RedisQueueLength"
        return_data = false

        metric_stat {
          metric {
            metric_name = "RedisQueueLength"
            namespace   = "ECSAutoscaling"

            dimensions {
              name  = "ClusterName"
              value = var.project_name
            }

            dimensions {
              name  = "ServiceName"
              value = "${var.project_name}-worker"
            }
          }

          stat = "Average"
        }
      }

      metrics {
        id          = "m2"
        label       = "RunningTaskCount"
        return_data = false

        metric_stat {
          metric {
            metric_name = "RunningTaskCount"
            namespace   = "ECS/ContainerInsights"

            dimensions {
              name  = "ClusterName"
              value = var.project_name
            }

            dimensions {
              name  = "ServiceName"
              value = "${var.project_name}-worker"
            }
          }

          stat = "Average"
        }
      }

      metrics {
        id          = "e1"
        label       = "BacklogPerWorker"
        expression  = "m1 / m2"
        return_data = true
      }
    }
  }
}
