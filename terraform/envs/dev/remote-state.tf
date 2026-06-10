provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = { Project = "akka-orders" Environment = "dev" }
  }
}

terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws"; version = "~> 5.0" }
  }
}

resource "aws_ecr_repository" "main" {
  name                 = "akka-orders-dev"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_s3_bucket" "artifacts" {
  bucket = "akka-orders-dev-artifacts-${data.aws_caller_identity.current.account_id}"
}

resource "aws_iam_user" "deploy" {
  name = "akka-orders-dev-deploy"
}

output "ecr_repo_url"  { value = aws_ecr_repository.main.repository_url }
output "artifacts_arn" { value = aws_s3_bucket.artifacts.arn }
output "account_id"    { value = data.aws_caller_identity.current.account_id }

data "aws_caller_identity" "current" {}
