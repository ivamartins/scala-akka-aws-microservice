package com.codesolutions.akka.aws.persistence

import com.codesolutions.akka.aws.domain.Order
import com.codesolutions.akka.aws.store.OrderStore

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Sealed response ADT — same pattern as akka-scala-base. Lets the HTTP
 * layer pattern-match exhaustively.
 */
sealed trait OrderResponse
final case class OrderFound(order: Order) extends OrderResponse
final case class OrderNotFound(orderId: String) extends OrderResponse
final case class OrderRejected(reason: String) extends OrderResponse

/**
 * OrderRepository — service layer between the HTTP routes and the
 * storage layer. Encapsulates validation + business rules.
 */
class OrderRepository(store: OrderStore)(implicit ec: ExecutionContext) {
  def create(id: String, customerId: String, amount: Double): Future[OrderResponse] = {
    if (amount <= 0) Future.successful(OrderRejected("amount must be > 0"))
    else {
      val o = Order(id, customerId, amount, "CREATED", Instant.now())
      store.get(id).flatMap {
        case Some(_) => Future.successful(OrderRejected(s"order $id already exists"))
        case None    => store.put(o).map(_ => OrderFound(o))
      }
    }
  }

  def get(id: String): Future[OrderResponse] =
    store.get(id).map {
      case Some(o) => OrderFound(o)
      case None    => OrderNotFound(id)
    }

  def changeStatus(id: String, newStatus: String): Future[OrderResponse] =
    store.get(id).flatMap {
      case Some(o) =>
        val updated = o.copy(status = newStatus)
        store.put(updated).map(_ => OrderFound(updated))
      case None => Future.successful(OrderNotFound(id))
    }

  def list(limit: Int): Future[Seq[Order]] = store.list(limit)
}
