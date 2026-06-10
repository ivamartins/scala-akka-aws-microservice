variable "name"               { type = string }
variable "image"              { type = string }
variable "container_port"     { type = number; default = 8080 }
variable "cpu"                { type = number; default = 256 }
variable "memory"             { type = number; default = 512 }
variable "desired_count"      { type = number; default = 2 }
variable "public_subnet_ids"  { type = list(string) }
variable "private_subnet_ids" { type = list(string) }
variable "vpc_id"             { type = string }

output "cluster_name"      { value = aws_ecs_cluster.main.name }
output "service_name"      { value = aws_ecs_service.main.name }
output "task_role_arn"     { value = aws_iam_role.task.arn }
output "execution_role_arn" { value = aws_iam_role.execution.arn }
output "alb_dns_name"      { value = aws_lb.main.dns_name }
output "alb_arn"           { value = aws_lb.main.arn }
output "target_group_arn"  { value = aws_lb_target_group.main.arn }
output "security_group_id" { value = aws_security_group.service.id }
