package com.codesolutions.akka.aws

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import com.codesolutions.akka.aws.api.OrderRoutes
import com.codesolutions.akka.aws.persistence.OrderRepository
import com.codesolutions.akka.aws.store.{DynamoOrderStore, InMemoryOrderStore, OrderStore}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.ExecutionContext

/**
 * Microservice entry point.
 *
 * Storage backend is chosen at boot time:
 *   - "in-memory"  : local dev, tests
 *   - "dynamodb"   : production (uses AWS SDK with IMDS / IRSA for credentials)
 *
 * The HTTP port is read from PORT env (default 8080) so it can be
 * configured by ECS task definition.
 */
object Main {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val port   = sys.env.getOrElse("PORT", config.getString("http.port"))
    val backend = sys.env.getOrElse("STORAGE_BACKEND", "in-memory")

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "akka-aws-microservice")
    implicit val ec: ExecutionContext = system.executionContext

    val store: OrderStore = backend match {
      case "dynamodb" =>
        val tableName = sys.env.getOrElse("DYNAMODB_TABLE", "orders")
        val region    = Region.of(sys.env.getOrElse("AWS_REGION", "us-east-1"))
        val client    = DynamoDbAsyncClient.builder().region(region).build()
        new DynamoOrderStore(client, tableName)
      case _ =>
        val s = new InMemoryOrderStore()
        s.seed()
        s
    }

    val repo = new OrderRepository(store)
    val routes = new OrderRoutes(repo).route

    Http().newServerAt("0.0.0.0", port.toInt).bind(routes)

    println(s"[akka-aws-microservice] listening on :$port, storage=$backend")
  }
}
