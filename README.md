# scala-akka-aws-microservice

[![CI](https://github.com/ivamartins/scala-akka-aws-microservice/actions/workflows/ci.yml/badge.svg)](https://github.com/ivamartins/scala-akka-aws-microservice/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Part of the **Code Solutions Event-Driven & Streaming Toolkit** product line. Reference Akka + AWS deployment with Fargate, Terraform, and ECR.

Scala + Akka Typed microservice reference, with end-to-end **AWS deployment** via Terraform (Fargate + ECR + ALB).

## Why this base

- **Akka Typed microservice** + Akka HTTP, ready for production
- **AWS deployment** — Terraform scripts for Fargate (ECS), ECR, and ALB
- **CI/CD-ready** — `.github/workflows/` included for build + push to ECR
- **Proven pattern** — used as the reference for cloud-native Akka services

## Quick start

**Prerequisites:** Java + sbt + Docker + AWS CLI (for deploy).

```bash
# Run locally
sbt run

# Build Docker image
docker build -t scala-akka-microservice:latest .

# Run with Docker
docker run -p 8080:8080 scala-akka-microservice:latest
```

## AWS deploy (Terraform)

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

Provisions:
- ECR repository
- ECS Fargate cluster + service
- Application Load Balancer
- CloudWatch logs

## CI/CD

`.github/workflows/` includes a workflow that:
1. Runs `sbt test` on every PR
2. Builds the Docker image
3. Pushes to ECR on `main`

## Run the tests

```bash
sbt test
```

> **Português?** Veja [`README.pt-BR.md`](./README.pt-BR.md).

## See also

- **Related base**: [akka-scala-base](https://github.com/ivamartins/akka-scala-base), [flink-kafka-scala-base](https://github.com/ivamartins/flink-kafka-scala-base)
- **Product line**: [Event-Driven & Streaming Toolkit](https://ivamartins.github.io/code-solutions-site/#produtos)
- **Code Solutions on LinkedIn**: [linkedin.com/company/code-solutions-it](https://www.linkedin.com/company/code-solutions-it/)
- **All Code Solutions open source**: [github.com/ivamartins](https://github.com/ivamartins)

## License

MIT — see `LICENSE`.
