module "network" {
  source      = "../../modules/network"
  name        = "${var.project_name}-${var.environment}"
  vpc_cidr    = "10.0.0.0/16"
  azs         = ["us-east-1a", "us-east-1b"]
}

module "storage" {
  source = "../../modules/storage"
  name   = "${var.project_name}-${var.environment}"
}

module "database" {
  source              = "../../modules/database"
  name                = "${var.project_name}-${var.environment}"
  private_subnet_ids  = module.network.private_subnet_ids
  vpc_id              = module.network.vpc_id
  db_username         = "akka"
  db_password         = var.db_password
}

module "ecs" {
  source              = "../../modules/ecs"
  name                = "${var.project_name}-${var.environment}"
  image               = var.container_image
  container_port      = 8080
  cpu                 = 256
  memory              = 512
  desired_count       = 2
  public_subnet_ids   = module.network.public_subnet_ids
  private_subnet_ids  = module.network.private_subnet_ids
  vpc_id              = module.network.vpc_id
}

module "observability" {
  source            = "../../modules/observability"
  name              = "${var.project_name}-${var.environment}"
  ecs_service_name  = module.ecs.service_name
  alb_arn_suffix    = module.ecs.alb_arn
}

output "alb_dns_name"     { value = module.ecs.alb_dns_name }
output "db_endpoint"      { value = module.database.db_endpoint }
output "orders_table"     { value = module.storage.orders_table_name }
