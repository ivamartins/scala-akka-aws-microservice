variable "name"               { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "vpc_id"             { type = string }
variable "db_username"        { type = string; default = "akka" }
variable "db_password"        { type = string; sensitive = true }
variable "engine_version"     { type = string; default = "15.5" }
variable "instance_class"     { type = string; default = "db.t3.micro" }

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db-subnets"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "db" {
  name   = "${var.name}-db-sg"
  vpc_id = var.vpc_id
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "main" {
  identifier              = "${var.name}-pg"
  engine                  = "postgres"
  engine_version          = var.engine_version
  instance_class          = var.instance_class
  allocated_storage       = 20
  storage_encrypted       = true
  username                = var.db_username
  password                = var.db_password
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  skip_final_snapshot     = true
  backup_retention_period = 7
  multi_az                = false
  publicly_accessible     = false
  tags = { Name = "${var.name}-pg" }
}

output "db_endpoint" { value = aws_db_instance.main.address }
output "db_port"     { value = aws_db_instance.main.port }
