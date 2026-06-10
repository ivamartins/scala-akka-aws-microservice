variable "project_name"     { type = string; default = "akka-orders" }
variable "container_image"  { type = string; default = "123456789012.dkr.ecr.us-east-1.amazonaws.com/akka-orders-dev:latest" }
variable "db_password"      { type = string; sensitive = true }
