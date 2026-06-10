variable "name"      { type = string }
variable "ecs_service_name" { type = string }
variable "alb_arn_suffix"    { type = string }
variable "sns_topic_arn"     { type = string; default = "" }

resource "aws_sns_topic" "alarms" {
  count = var.sns_topic_arn == "" ? 1 : 0
  name  = "${var.name}-alarms"
}

locals {
  effective_sns_arn = var.sns_topic_arn != "" ? var.sns_topic_arn : try(aws_sns_topic.alarms[0].arn, "")
}

# CPU > 80% for 5 minutes
resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  alarm_name          = "${var.name}-ecs-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "ECS service CPU > 80% for 2 minutes"
  dimensions = {
    ClusterName = var.name
    ServiceName = var.ecs_service_name
  }
  alarm_actions = [local.effective_sns_arn]
}

# 5xx errors from ALB > 1%
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.name}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_ELB_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "ALB returning > 10 5xx errors/min"
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
  alarm_actions = [local.effective_sns_arn]
}

# Unhealthy ALB targets > 0
resource "aws_cloudwatch_metric_alarm" "alb_unhealthy" {
  alarm_name          = "${var.name}-alb-unhealthy"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Average"
  threshold           = 0
  alarm_description   = "Unhealthy ALB targets"
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
  alarm_actions = [local.effective_sns_arn]
}

output "sns_topic_arn" { value = local.effective_sns_arn }
