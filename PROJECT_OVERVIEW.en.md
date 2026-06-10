# scala-akka-aws-microservice — Overview & flow

Production-ready **Scala 2.13 + Akka HTTP** microservice deployable to **AWS Fargate**, with **DynamoDB** (primary), **RDS PostgreSQL** optional, **CloudWatch** observability, full **Terraform** IaC and **GitHub Actions** CI/CD. Java/Scala/Akka, REST APIs, high availability, AWS, CI/CD.

## Stack (with versions)

- **Scala 2.13.14**
- **sbt** (version in `project/build.properties`)
- **sbt-assembly 2.2.0** (fat jar for Fargate)
- **Akka 2.8.5** (Actor Typed, Stream, Persistence Typed, Serialization Jackson, TestKit Typed, Persistence TestKit)
- **Akka HTTP 10.5.3** (Core, Spray-JSON, TestKit)
- **AWS SDK v2 2.25.40** — `dynamodb`, `rds`, `cloudwatch`, `sts`
- **Spray-JSON 1.3.6**
- **Logback 1.4.14**
- **ScalaTest 3.2.17**
- **Docker** (multi-stage: sbt assembly → JRE)
- **Terraform** (network, ecs, storage, database, observability, envs/dev)
- **GitHub Actions** (ci.yml, cd.yml) + OIDC AWS (no long-lived keys)

---

## Main flow

### 1. Bootstrap (`Main.scala`)
- Reads envs: `PORT` (default 8080), `STORAGE_BACKEND` (default `in-memory`), `DYNAMODB_TABLE` (default `orders`), `AWS_REGION` (default `us-east-1`).
- Creates `ActorSystem` (Akka Typed, `Behaviors.empty` named `akka-aws-microservice`).
- Selects the `OrderStore`:
  - `"dynamodb"` → instantiates `DynamoDbAsyncClient` (IRSA/IMDS for credentials) and uses `DynamoOrderStore(client, tableName)`.
  - default → `InMemoryOrderStore` (with seed data for dev).
- Creates `OrderRepository(store)`, instantiates `OrderRoutes(repo)`, starts Akka HTTP on `0.0.0.0:$port`.

### 2. HTTP layer (`api/OrderRoutes`)
Typed routes with sealed ADT of responses:
- `GET /health` — liveness (200 "ok").
- `GET /orders` — lists up to 50 orders.
- `POST /orders` — creates order (`OrderFound` → 201, `OrderRejected` → 400).
- `GET /orders/:id` — read (`OrderFound` → 200, `OrderNotFound` → 404, `OrderRejected` → 400).
- `PUT /orders/:id` — updates status.

JSON via Spray-JSON: `Order` (`jsonFormat5`), `CreateOrderRequest` (`jsonFormat3`), `UpdateOrderRequest` (`jsonFormat1`), `Instant` as ISO-8601 string.

### 3. Repository pattern (`persistence/OrderRepository`)
- Encapsulates the `OrderStore` (trait), exposing domain operations: `create`, `get`, `list`, `changeStatus`.
- Returns ADT `OrderResponse` (`OrderFound | OrderNotFound | OrderRejected`).
- Validates amount > 0 and order existence.

### 4. Storage layer (`store/`)
- `OrderStore.scala` — trait.
- `InMemoryOrderStore.scala` — for dev/tests, with `seed()`.
- `DynamoOrderStore.scala` — production, against AWS SDK v2 DynamoDB async.

### 5. Deploy
- **Dockerfile** multi-stage: runs `sbt assembly` and copies the fat jar into a JRE image.
- **Terraform** provisions:
  - `network/` — VPC, public/private subnets in 2 AZs, NAT, IGW.
  - `ecs/` — Fargate cluster, task definition, service, ALB.
  - `storage/` — DynamoDB table `akka-orders-<env>-orders` (PAY_PER_REQUEST, PITR enabled).
  - `database/` — RDS PostgreSQL (optional).
  - `observability/` — CloudWatch alarms (CPU > 80%, ALB 5xx, unhealthy hosts) → SNS.
- **CI** (`.github/workflows/ci.yml`): `sbt test`, `terraform fmt -check -recursive`, `terraform validate`.
- **CD** (`.github/workflows/cd.yml`): image build, push to ECR, render new task definition, rolling update via `aws-actions/amazon-ecs-deploy-task-definition`.

### 6. CI/CD with OIDC
No long-lived keys in GitHub — uses OIDC AWS for temporary credentials.

---

## Endpoints

| Method | Path           | Description                                |
|--------|----------------|--------------------------------------------|
| GET    | `/health`      | Liveness (returns "ok")                    |
| GET    | `/orders`      | Lists up to 50 orders                      |
| POST   | `/orders`      | Create order                               |
| GET    | `/orders/:id`  | Get order                                  |
| PUT    | `/orders/:id`  | Update status                              |

---

## What's in each subfolder

### Root
- `build.sbt` — Scala 2.13.14, Akka 2.8.5, AWS SDK 2.25.40, sbt-assembly 2.2.0.
- `project/build.properties` — sbt version.
- `project/plugins.sbt` — `sbt-assembly 2.2.0`.
- `Dockerfile` — multi-stage (sbt assembly → JRE).
- `README.md` — quickstart, deploy, architecture.
- `.gitignore`, `.connection-test`.

### `src/main/resources/`
- `application.conf` — `http.port` and Akka configs.

### `src/main/scala/com/codesolutions/akka/aws/`
- `Main.scala` — entry point: chooses storage backend, starts Akka HTTP.

### `src/main/scala/com/codesolutions/akka/aws/domain/`
- `Order.scala` — `case class Order(orderId, customerId, amount, status, createdAt)`.

### `src/main/scala/com/codesolutions/akka/aws/persistence/`
- `OrderRepository.scala` — `OrderRepository(store)` with `create/get/list/changeStatus`, returns ADT `OrderResponse` (sealed trait with `OrderFound`, `OrderNotFound`, `OrderRejected`).

### `src/main/scala/com/codesolutions/akka/aws/store/`
- `OrderStore.scala` — trait (`create`, `get`, `list`, `update`, `delete`).
- `InMemoryOrderStore.scala` — impl with `Map` + `seed()`.
- `DynamoOrderStore.scala` — impl with AWS SDK v2 `DynamoDbAsyncClient`.

### `src/main/scala/com/codesolutions/akka/aws/api/`
- `OrderRoutes.scala` — REST routes, JSON via Spray-JSON, typed error handling.

### `src/test/scala/com/codesolutions/akka/aws/`
- `OrderRepositorySpec.scala` — repository tests.
- `OrderRoutesSpec.scala` — HTTP route tests.

### `terraform/`
- `main.tf` — root orchestrator module.
- `modules/network/` — VPC, subnets, NAT, IGW (2 AZs).
- `modules/ecs/` — Fargate cluster, task definition, service, ALB.
- `modules/storage/` — DynamoDB `akka-orders-<env>-orders` (PAY_PER_REQUEST, PITR).
- `modules/database/` — RDS PostgreSQL (optional).
- `modules/observability/` — CloudWatch alarms → SNS.
- `envs/dev/main.tf` — module composition for dev.
- `envs/dev/variables.tf` — `db_password`, `container_image`.
- `envs/dev/remote-state.tf` — remote backend (S3 + DynamoDB lock).

### `ecs/`
- `task-definition.json` — ECS task definition (template used by CD).

### `.github/workflows/`
- `ci.yml` — `sbt test`, `terraform fmt -check -recursive`, `terraform validate`.
- `cd.yml` — image build, push ECR, render task definition, rolling deploy via `amazon-ecs-deploy-task-definition`.

---

## How to run locally

```bash
sbt test   # 10 tests
sbt run    # starts on :8080 with in-memory store
```

```bash
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"o-1","customerId":"c-1","amount":99.9}'

curl http://localhost:8080/orders/o-1
curl -X PUT http://localhost:8080/orders/o-1 \
  -H 'Content-Type: application/json' \
  -d '{"status":"PAID"}'
```

## How to deploy

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

Provisions: VPC + subnets in 2 AZs, ALB in public subnets, Fargate in private, DynamoDB `akka-orders-dev-orders`, optional RDS, CloudWatch alarms.

CI runs on every push to `main`; CD builds/pushes the image, renders the task definition and performs rolling deploy (OIDC, no long-lived keys).
