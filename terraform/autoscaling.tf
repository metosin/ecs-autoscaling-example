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
    scale_out_cooldown = 30
    scale_in_cooldown  = 60
    disable_scale_in   = var.scale_in_policy == "step"

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

resource "aws_cloudwatch_metric_alarm" "worker_scale_in" {
  count = var.scale_in_policy == "step" ? 1 : 0

  alarm_name        = "${var.project_name}-worker-scale-in"
  alarm_description = "BacklogPerWorker below target — trigger step scale-in"
  comparison_operator = "LessThanThreshold"
  threshold           = var.autoscaling_target_value
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  treat_missing_data  = "missing"
  alarm_actions       = [aws_appautoscaling_policy.worker_step_scale_in[0].arn]

  metric_query {
    id          = "m1"
    label       = "RedisQueueLength"
    return_data = false

    metric {
      metric_name = "RedisQueueLength"
      namespace   = "ECSAutoscaling"
      period      = 60
      stat        = "Average"

      dimensions = {
        ClusterName = var.project_name
        ServiceName = "${var.project_name}-worker"
      }
    }
  }

  metric_query {
    id          = "m2"
    label       = "RunningTaskCount"
    return_data = false

    metric {
      metric_name = "RunningTaskCount"
      namespace   = "ECS/ContainerInsights"
      period      = 60
      stat        = "Average"

      dimensions = {
        ClusterName = var.project_name
        ServiceName = "${var.project_name}-worker"
      }
    }
  }

  metric_query {
    id          = "e1"
    label       = "BacklogPerWorker"
    expression  = "m1 / m2"
    return_data = true
  }
}

resource "aws_appautoscaling_policy" "worker_step_scale_in" {
  count = var.scale_in_policy == "step" ? 1 : 0

  name               = "${var.project_name}-worker-step-scale-in"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.worker.resource_id
  scalable_dimension = aws_appautoscaling_target.worker.scalable_dimension
  service_namespace  = aws_appautoscaling_target.worker.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Average"

    step_adjustment {
      scaling_adjustment          = -1
      metric_interval_upper_bound = 0
    }
  }
}
