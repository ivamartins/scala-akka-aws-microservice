# scala-akka-aws-microservice — Visão geral e fluxo

Microsserviço production-ready em **Scala 2.13 + Akka HTTP** deployável em **AWS Fargate**, com **DynamoDB** (primary), **RDS PostgreSQL** opcional, **CloudWatch** observability, **Terraform** IaC completo e **GitHub Actions** CI/CD. Java/Scala/Akka, REST APIs, alta disponibilidade, AWS, CI/CD.

## Stack (com versões)

- **Scala 2.13.14**
- **sbt** (versão em `project/build.properties`)
- **sbt-assembly 2.2.0** (fat jar para Fargate)
- **Akka 2.8.5** (Actor Typed, Stream, Persistence Typed, Serialization Jackson, TestKit Typed, Persistence TestKit)
- **Akka HTTP 10.5.3** (Core, Spray-JSON, TestKit)
- **AWS SDK v2 2.25.40** — `dynamodb`, `rds`, `cloudwatch`, `sts`
- **Spray-JSON 1.3.6**
- **Logback 1.4.14**
- **ScalaTest 3.2.17**
- **Docker** (multi-stage: sbt assembly → JRE)
- **Terraform** (network, ecs, storage, database, observability, envs/dev)
- **GitHub Actions** (ci.yml, cd.yml) + OIDC AWS (sem long-lived keys)

---

## Fluxo principal

### 1. Bootstrap (`Main.scala`)
- Lê envs: `PORT` (default 8080), `STORAGE_BACKEND` (default `in-memory`), `DYNAMODB_TABLE` (default `orders`), `AWS_REGION` (default `us-east-1`).
- Cria `ActorSystem` (Akka Typed, `Behaviors.empty` chamado `akka-aws-microservice`).
- Seleciona o `OrderStore`:
  - `"dynamodb"` → instancia `DynamoDbAsyncClient` (IRSA/IMDS para credenciais) e usa `DynamoOrderStore(client, tableName)`.
  - default → `InMemoryOrderStore` (com seed de dados para dev).
- Cria `OrderRepository(store)`, instancia `OrderRoutes(repo)`, sobe Akka HTTP em `0.0.0.0:$port`.

### 2. Camada HTTP (`api/OrderRoutes`)
Rotas tipadas com sealed ADT de respostas:
- `GET /health` — liveness (200 "ok").
- `GET /orders` — lista até 50 pedidos.
- `POST /orders` — cria pedido (`OrderFound` → 201, `OrderRejected` → 400).
- `GET /orders/:id` — busca (`OrderFound` → 200, `OrderNotFound` → 404, `OrderRejected` → 400).
- `PUT /orders/:id` — atualiza status.

JSON via Spray-JSON: `Order` (`jsonFormat5`), `CreateOrderRequest` (`jsonFormat3`), `UpdateOrderRequest` (`jsonFormat1`), `Instant` em ISO-8601 string.

### 3. Repository pattern (`persistence/OrderRepository`)
- Encapsula o `OrderStore` (trait), expondo operações de domínio: `create`, `get`, `list`, `changeStatus`.
- Retorna ADT `OrderResponse` (`OrderFound | OrderNotFound | OrderRejected`).
- Valida amount > 0 e ordem existente.

### 4. Storage layer (`store/`)
- `OrderStore.scala` — trait.
- `InMemoryOrderStore.scala` — para dev/testes, com `seed()`.
- `DynamoOrderStore.scala` — produção, contra AWS SDK v2 DynamoDB async.

### 5. Deploy
- **Dockerfile** multi-stage: roda `sbt assembly` e copia o fat jar para imagem JRE.
- **Terraform** provisiona:
  - `network/` — VPC, subnets públicas/privadas em 2 AZs, NAT, IGW.
  - `ecs/` — cluster Fargate, task definition, service, ALB.
  - `storage/` — tabela DynamoDB `akka-orders-<env>-orders` (PAY_PER_REQUEST, PITR habilitado).
  - `database/` — RDS PostgreSQL (opcional).
  - `observability/` — alarmes CloudWatch (CPU > 80%, ALB 5xx, unhealthy hosts) → SNS.
- **CI** (`.github/workflows/ci.yml`): `sbt test`, `terraform fmt -check -recursive`, `terraform validate`.
- **CD** (`.github/workflows/cd.yml`): build da imagem, push para ECR, render do novo task definition, rolling update via `aws-actions/amazon-ecs-deploy-task-definition`.

### 6. CI/CD com OIDC
Sem long-lived keys no GitHub — usa OIDC AWS para credentials temporárias.

---

## Endpoints

| Método | Path           | Descrição                                |
|--------|----------------|------------------------------------------|
| GET    | `/health`      | Liveness (retorna "ok")                  |
| GET    | `/orders`      | Lista até 50 pedidos                     |
| POST   | `/orders`      | Criar pedido                             |
| GET    | `/orders/:id`  | Buscar pedido                            |
| PUT    | `/orders/:id`  | Atualizar status                         |

---

## O que tem em cada subpasta

### Raiz
- `build.sbt` — Scala 2.13.14, Akka 2.8.5, AWS SDK 2.25.40, sbt-assembly 2.2.0.
- `project/build.properties` — versão sbt.
- `project/plugins.sbt` — `sbt-assembly 2.2.0`.
- `Dockerfile` — multi-stage (sbt assembly → JRE).
- `README.md` — quickstart, deploy, arquitetura.
- `.gitignore`, `.connection-test`.

### `src/main/resources/`
- `application.conf` — `http.port` e configs do Akka.

### `src/main/scala/com/codesolutions/akka/aws/`
- `Main.scala` — entry point: escolhe storage backend, sobe Akka HTTP.

### `src/main/scala/com/codesolutions/akka/aws/domain/`
- `Order.scala` — `case class Order(orderId, customerId, amount, status, createdAt)`.

### `src/main/scala/com/codesolutions/akka/aws/persistence/`
- `OrderRepository.scala` — `OrderRepository(store)` com `create/get/list/changeStatus`, retorna ADT `OrderResponse` (sealed trait com `OrderFound`, `OrderNotFound`, `OrderRejected`).

### `src/main/scala/com/codesolutions/akka/aws/store/`
- `OrderStore.scala` — trait (`create`, `get`, `list`, `update`, `delete`).
- `InMemoryOrderStore.scala` — impl com `Map` + `seed()`.
- `DynamoOrderStore.scala` — impl com AWS SDK v2 `DynamoDbAsyncClient`.

### `src/main/scala/com/codesolutions/akka/aws/api/`
- `OrderRoutes.scala` — rotas REST, JSON via Spray-JSON, error handling tipado.

### `src/test/scala/com/codesolutions/akka/aws/`
- `OrderRepositorySpec.scala` — testes do repository.
- `OrderRoutesSpec.scala` — testes das rotas HTTP.

### `terraform/`
- `main.tf` — root module orquestrador.
- `modules/network/` — VPC, subnets, NAT, IGW (2 AZs).
- `modules/ecs/` — cluster Fargate, task definition, service, ALB.
- `modules/storage/` — DynamoDB `akka-orders-<env>-orders` (PAY_PER_REQUEST, PITR).
- `modules/database/` — RDS PostgreSQL (opcional).
- `modules/observability/` — alarmes CloudWatch → SNS.
- `envs/dev/main.tf` — composição dos módulos para dev.
- `envs/dev/variables.tf` — `db_password`, `container_image`.
- `envs/dev/remote-state.tf` — backend remoto (S3 + lock DynamoDB).

### `ecs/`
- `task-definition.json` — task definition ECS (template usado pelo CD).

### `.github/workflows/`
- `ci.yml` — `sbt test`, `terraform fmt -check -recursive`, `terraform validate`.
- `cd.yml` — build da imagem, push ECR, render task definition, rolling deploy via `amazon-ecs-deploy-task-definition`.

---

## Como rodar localmente

```bash
sbt test   # 10 testes
sbt run    # sobe em :8080 com in-memory store
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

## Como fazer deploy

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

Provisiona: VPC + subnets em 2 AZs, ALB em subnets públicas, Fargate em privadas, DynamoDB `akka-orders-dev-orders`, RDS opcional, alarmes CloudWatch.

CI roda em todo push para `main`; CD faz build/push da imagem, render do task definition e rolling deploy (OIDC, sem long-lived keys).
