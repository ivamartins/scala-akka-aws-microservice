# Fluxo de interação entre classes — scala-akka-aws-microservice

Visualização rápida de como uma requisição HTTP atravessa o sistema até o storage backend (DynamoDB ou in-memory).

## 1. Bootstrap

```
Main.main()
  ├─> lê envs: PORT, STORAGE_BACKEND, DYNAMODB_TABLE, AWS_REGION
  ├─> ActorSystem(Behaviors.empty, "akka-aws-microservice")
  ├─> cria OrderStore:
  │     ├─ "dynamodb"  ──> DynamoOrderStore(DynamoDbAsyncClient, tableName)
  │     └─ default     ──> InMemoryOrderStore (com seed)
  ├─> cria OrderRepository(store)
  ├─> cria OrderRoutes(repo)
  └─> Http().newServerAt(0.0.0.0, port).bind(routes)
```

## 2. Health — `GET /health`

```
Cliente HTTP
  └─> OrderRoutes.path("health")        [api/OrderRoutes]
        └─> complete(200, "ok")
```

**Caminho resumido:**
`OrderRoutes (resposta estática)`

## 3. Criar pedido — `POST /orders`

```
Cliente HTTP (curl)
  └─> OrderRoutes.post()                [api/OrderRoutes]
        └─> OrderRepository.create()    [persistence/OrderRepository]
              └─> OrderStore.create()   [store/OrderStore]
                    ├─ InMemoryOrderStore: Map.put
                    └─ DynamoOrderStore:  DynamoDbAsyncClient.putItem  ──> DynamoDB
```

**Caminho resumido:**
`OrderRoutes → OrderRepository → OrderStore (InMemory | Dynamo) → (Map | DynamoDB)`

## 4. Buscar pedido — `GET /orders/:id`

```
Cliente HTTP
  └─> OrderRoutes.get()                 [api/OrderRoutes]
        └─> OrderRepository.get()       [persistence/OrderRepository]
              └─> OrderStore.get()      [store/OrderStore]
                    ├─ InMemoryOrderStore: Map.get
                    └─ DynamoOrderStore:  DynamoDbAsyncClient.getItem  ──> DynamoDB
```

## 5. Listar pedidos — `GET /orders`

```
Cliente HTTP
  └─> OrderRoutes.get(list)             [api/OrderRoutes]
        └─> OrderRepository.list(50)    [persistence/OrderRepository]
              └─> OrderStore.list()     [store/OrderStore]
                    ├─ InMemoryOrderStore: Map.values
                    └─ DynamoOrderStore:  DynamoDbAsyncClient.scan    ──> DynamoDB
```

## 6. Mudar status — `PUT /orders/:id`

```
Cliente HTTP
  └─> OrderRoutes.put()                 [api/OrderRoutes]
        └─> OrderRepository.changeStatus()  [persistence/OrderRepository]
              └─> OrderStore.update()   [store/OrderStore]
                    ├─ InMemoryOrderStore: Map.update
                    └─ DynamoOrderStore:  DynamoDbAsyncClient.updateItem ──> DynamoDB
```

## 7. Respostas tipadas (sealed ADT)

```
OrderRepository ──> OrderResponse (sealed trait)
                       ├── OrderFound(order)        ──> 201 / 200
                       ├── OrderNotFound(id)        ──> 404
                       └── OrderRejected(reason)    ──> 400
```

`OrderRoutes` faz `match` em `OrderResponse` e devolve o HTTP status correto via `onSuccess`.

## Mapa de pacotes

```
com.codesolutions.akka.aws
├── Main.scala                          ← entry point (escolhe backend)
├── domain/
│   └── Order.scala                     ← case class
├── persistence/
│   └── OrderRepository.scala           ← OrderRepository(store) + OrderResponse ADT
├── store/
│   ├── OrderStore.scala                ← trait
│   ├── InMemoryOrderStore.scala        ← impl local (Map + seed)
│   └── DynamoOrderStore.scala          ← impl prod (AWS SDK v2 async)
└── api/
    └── OrderRoutes.scala               ← Akka HTTP routes + JSON protocols
```

## Erros

`OrderRoutes` mapeia o sealed ADT `OrderResponse` para HTTP status (`OrderFound → 200/201`, `OrderNotFound → 404`, `OrderRejected → 400`). JSON malformado retorna 400 via `Spray-JSON` `deserializationError`.

## Deploy

```
Cliente ──> ALB (port 443) ──> Fargate task (Akka HTTP :8080) ──> DynamoDB
                                                              └─> CloudWatch (logs/alarms)
```

CD (`.github/workflows/cd.yml`): `sbt-assembly` → Docker multi-stage → push ECR → render task-definition → rolling deploy via `amazon-ecs-deploy-task-definition`. Credenciais AWS via OIDC (sem long-lived keys).
