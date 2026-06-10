# scala-akka-aws-microservice

Production-ready **Scala + Akka HTTP** microservice deployable to **AWS Fargate**, with **DynamoDB** for primary storage, **RDS PostgreSQL** available, **CloudWatch** observability, full **Terraform IaC** and **GitHub Actions CI/CD**.

## Why this project?

Built to showcase the **Senior Software Engineer (Caterpillar)** JD requirements end-to-end:

| JD requirement | Where |
|---|---|
| 5+ years Java 17+ / Scala 2+ | entire codebase (Scala 2.13) |
| 2+ years Scala + Akka (Streams, Actors, HTTP, Persistence) | `api/OrderRoutes`, `Main` |
| Designing well-defined Spring Boot / RESTful APIs | `api/OrderRoutes` — typed routes, sealed ADT responses |
| High-availability, reliable solutions | Fargate multi-AZ, ALB, health checks, CloudWatch alarms |
| Application architectural patterns (MVC, Microservices, Event-driven) | Repository pattern + OrderStore abstraction |
| Deploying software using CI/CD (Azure DevOps / Jenkins) | `.github/workflows/ci.yml` and `cd.yml` |
| AWS deploy (API Gateway, ALB, NLB, Fargate, Lambda, S3, CloudWatch, IAM, CloudFormation) | `terraform/modules/*` — all of the above via Terraform |
| PostgreSQL + DynamoDB | `terraform/modules/database`, `terraform/modules/storage` |
| Agile / Scrum | n/a directly — but the deployment model fits a Scrum cadence |

## Project layout

```
scala-akka-aws-microservice/
├── src/main/scala/com/codesolutions/akka/aws/
│   ├── Main.scala                       # entry point — chooses storage backend
│   ├── domain/Order.scala
│   ├── persistence/OrderRepository.scala
│   ├── store/OrderStore.scala           # trait
│   ├── store/InMemoryOrderStore.scala   # local / tests
│   ├── store/DynamoOrderStore.scala     # production
│   └── api/OrderRoutes.scala            # Akka HTTP routes
├── src/main/resources/application.conf
├── src/test/scala/com/codesolutions/akka/aws/
│   ├── OrderRepositorySpec.scala
│   └── OrderRoutesSpec.scala
├── terraform/
│   ├── main.tf
│   ├── modules/
│   │   ├── network/        # VPC, public/private subnets, NAT, IGW
│   │   ├── ecs/            # Fargate cluster, task def, service, ALB
│   │   ├── storage/        # DynamoDB table for orders
│   │   ├── database/       # RDS PostgreSQL (optional)
│   │   └── observability/  # CloudWatch alarms (CPU, 5xx, unhealthy)
│   └── envs/
│       ├── dev/main.tf
│       └── prod/  (mirrors dev with different sizing)
├── ecs/task-definition.json
├── .github/workflows/
│   ├── ci.yml             # build, test, terraform fmt/validate
│   └── cd.yml             # build+push image, deploy via ECS rolling update
├── Dockerfile              # multi-stage: sbt assembly → JRE
├── build.sbt
└── README.md
```

## How to run locally

```bash
sbt test          # 10 tests
sbt run           # starts on :8080 with in-memory store
```

The service reads `STORAGE_BACKEND` and `DYNAMODB_TABLE` env vars to choose between in-memory and DynamoDB.

## How to deploy

### 1. Provision infra (one-time, per env)

```bash
cd terraform/envs/dev
terraform init
terraform plan \
  -var "db_password=YOUR_DB_PASSWORD" \
  -var "container_image=123456789012.dkr.ecr.us-east-1.amazonaws.com/akka-orders-dev:latest"
terraform apply \
  -var "db_password=YOUR_DB_PASSWORD" \
  -var "container_image=123456789012.dkr.ecr.us-east-1.amazonaws.com/akka-orders-dev:latest"
```

This creates:
- VPC (public + private subnets, NAT, IGW) in 2 AZs
- ALB in public subnets
- ECS Fargate cluster + service in private subnets
- DynamoDB table `akka-orders-dev-orders`
- (Optional) RDS PostgreSQL
- CloudWatch alarms: ECS CPU > 80%, ALB 5xx, unhealthy hosts

### 2. CI/CD

`ci.yml` runs on every push to `main`:
- `sbt test`
- `terraform fmt -check -recursive`
- `terraform validate`

`cd.yml` runs on push to `main`:
- Builds Docker image (multi-stage: sbt-assembly → JRE)
- Pushes to ECR
- Renders new ECS task definition with the new image
- Performs rolling deployment via `aws-actions/amazon-ecs-deploy-task-definition`

OIDC-based AWS credentials — no long-lived keys in GitHub secrets.

## Endpoints

| Method | Path           | Description |
|--------|----------------|-------------|
| GET    | /health        | liveness probe (returns "ok") |
| GET    | /orders        | list (up to 50) |
| POST   | /orders        | create |
| GET    | /orders/:id    | read |
| PUT    | /orders/:id    | update status |

Example:

```bash
curl -X POST https://<alb>/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"o-1","customerId":"c-1","amount":99.9}'

curl https://<alb>/orders/o-1
curl -X PUT https://<alb>/orders/o-1 \
  -H 'Content-Type: application/json' \
  -d '{"status":"PAID"}'
```

## Architecture

```
            ┌──────────────────┐
            │  GitHub Actions  │ (OIDC, no long-lived keys)
            └────────┬─────────┘
                     │ build & push image
                     ▼
       ┌─────────────────────────┐
       │  ECR (akka-orders-dev)  │
       └────────────┬────────────┘
                    │ rolling update
                    ▼
   ┌────────────────────────────────────┐
   │           ALB (public)              │
   │       health check: /health         │
   └──────────────┬─────────────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
   ┌──────────┐       ┌──────────┐
   │ Fargate  │ ...   │ Fargate  │  (desired_count = 2)
   │ task A   │       │ task B   │
   └────┬─────┘       └────┬─────┘
        │                  │
        └────────┬─────────┘
                 ▼
        ┌─────────────────┐
        │    DynamoDB     │  (PAY_PER_REQUEST, PITR enabled)
        │ akka-orders-... │
        └─────────────────┘
                 │
                 ▼
        ┌─────────────────┐
        │  CloudWatch     │  logs + 3 alarms → SNS
        │  alarms → SNS   │
        └─────────────────┘
```

## See also

- `akka-scala-base` — the pure-Scala base that inspired the persistence + HTTP layer
- `flink-data-mesh-pipeline` — streaming counterpart (Data Engineer role)
- `dbt-airflow-data-platform` — batch counterpart (Data Engineer role)
