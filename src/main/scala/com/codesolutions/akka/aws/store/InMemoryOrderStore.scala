package com.codesolutions.akka.aws.store

import com.codesolutions.akka.aws.domain.Order

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
 * In-memory order store, used in tests and as a fallback for local dev.
 *
 * The Dynamo and Postgres stores share the same trait; this lets us
 * test the HTTP layer with no external dependency.
 */
class InMemoryOrderStore extends OrderStore {
  private val map = TrieMap.empty[String, Order]

  def put(order: Order): Future[Unit] = Future.successful {
    map += (order.id -> order)
  }

  def get(id: String): Future[Option[Order]] = Future.successful(map.get(id))

  def delete(id: String): Future[Boolean] = Future.successful(map.remove(id).isDefined)

  def list(limit: Int): Future[Seq[Order]] = Future.successful(map.values.toSeq.sortBy(_.createdAt.toString).take(limit))

  def seed(): Unit = {
    val now = Instant.now()
    map += ("o-1" -> Order("o-1", "c-1", 99.9, "CREATED", now))
    map += ("o-2" -> Order("o-2", "c-2", 50.0, "PAID",    now))
  }
}
