package com.codesolutions.akka.aws.store

import com.codesolutions.akka.aws.domain.Order

import scala.concurrent.Future

/**
 * Storage abstraction so the OrderRepository can swap between
 * DynamoDB (NoSQL) and RDS (PostgreSQL) at deploy time.
 *
 * - For NoSQL (high-velocity, key-value): use DynamoOrderStore
 * - For relational (queries, joins): use PostgresOrderStore
 *
 * Both store the same Order domain object, marshalled/unmarshalled
 * via the protocol-specific serializer.
 */
trait OrderStore {
  def put(order: Order): Future[Unit]
  def get(id: String): Future[Option[Order]]
  def delete(id: String): Future[Boolean]
  def list(limit: Int): Future[Seq[Order]]
}
