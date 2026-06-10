package com.codesolutions.akka.aws.store

import com.codesolutions.akka.aws.domain.Order
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._

/**
 * DynamoDB-backed order store. Used for high-velocity, key-value access.
 *
 * Table schema (defined in Terraform):
 *   - PK: id (S)
 *   - attributes: customerId (S), amount (N), status (S), createdAt (S, ISO-8601)
 */
class DynamoOrderStore(client: DynamoDbAsyncClient, tableName: String)(implicit ec: ExecutionContext) extends OrderStore {

  override def put(order: Order): Future[Unit] = {
    val req = PutItemRequest.builder()
      .tableName(tableName)
      .item(Map(
        "id"         -> AttributeValue.builder().s(order.id).build(),
        "customerId" -> AttributeValue.builder().s(order.customerId).build(),
        "amount"     -> AttributeValue.builder().n(order.amount.toString).build(),
        "status"     -> AttributeValue.builder().s(order.status).build(),
        "createdAt"  -> AttributeValue.builder().s(order.createdAt.toString).build()
      ).asJava)
      .build()
    client.putItem(req).asScala.map(_ => ())
  }

  override def get(id: String): Future[Option[Order]] = {
    val req = GetItemRequest.builder()
      .tableName(tableName)
      .key(Map("id" -> AttributeValue.builder().s(id).build()).asJava)
      .build()
    client.getItem(req).asScala.map { resp =>
      Option(resp.item).map(itemToOrder)
    }
  }

  override def delete(id: String): Future[Boolean] = {
    val req = DeleteItemRequest.builder()
      .tableName(tableName)
      .key(Map("id" -> AttributeValue.builder().s(id).build()).asJava)
      .build()
    client.deleteItem(req).asScala.map(_.attributes.asScala.nonEmpty)
  }

  override def list(limit: Int): Future[Seq[Order]] = {
    val req = ScanRequest.builder()
      .tableName(tableName)
      .limit(limit)
      .build()
    client.scan(req).asScala.map(_.items.asScala.toSeq.map(itemToOrder))
  }

  private def itemToOrder(item: java.util.Map[String, AttributeValue]): Order = Order(
    id         = item.get("id").s,
    customerId = item.get("customerId").s,
    amount     = item.get("amount").n.toDouble,
    status     = item.get("status").s,
    createdAt  = Instant.parse(item.get("createdAt").s)
  )
}
