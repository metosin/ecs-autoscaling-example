variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "autoscaling-example"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-1"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones"
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1c"]
}

variable "image_tag" {
  description = "Docker image tag (e.g. git tag)"
  type        = string
}

variable "web_image" {
  description = "Docker image for web service (overrides ECR + image_tag)"
  type        = string
  default     = ""
}

variable "worker_image" {
  description = "Docker image for worker service (overrides ECR + image_tag)"
  type        = string
  default     = ""
}

variable "web_cpu" {
  description = "CPU units for web task"
  type        = number
  default     = 256
}

variable "web_memory" {
  description = "Memory (MiB) for web task"
  type        = number
  default     = 512
}

variable "worker_cpu" {
  description = "CPU units for worker task"
  type        = number
  default     = 256
}

variable "worker_memory" {
  description = "Memory (MiB) for worker task"
  type        = number
  default     = 512
}

variable "worker_min_capacity" {
  description = "Minimum worker tasks"
  type        = number
  default     = 1
}

variable "worker_max_capacity" {
  description = "Maximum worker tasks"
  type        = number
  default     = 20
}

variable "autoscaling_target_value" {
  description = "Target queue items per worker"
  type        = number
  default     = 5
}

variable "scale_in_policy" {
  description = "Scale-in policy type: target_tracking or step"
  type        = string
  default     = "target_tracking"
  validation {
    condition     = contains(["target_tracking", "step"], var.scale_in_policy)
    error_message = "Must be target_tracking or step."
  }
}

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}
