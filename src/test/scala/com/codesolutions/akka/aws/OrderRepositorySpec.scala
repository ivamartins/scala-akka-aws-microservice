package com.codesolutions.akka.aws

import com.codesolutions.akka.aws.persistence.{OrderFound, OrderRepository, OrderRejected}
import com.codesolutions.akka.aws.store.InMemoryOrderStore
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await

class OrderRepositorySpec extends AnyWordSpec with Matchers {

  "OrderRepository" should {

    "create a new order" in {
      val repo = new OrderRepository(new InMemoryOrderStore())
      val r = Await.result(repo.create("o-x", "c-1", 100.0), 5.seconds)
      r shouldBe a [OrderFound]
    }

    "reject non-positive amount" in {
      val repo = new OrderRepository(new InMemoryOrderStore())
      val r = Await.result(repo.create("o-y", "c-1", 0), 5.seconds)
      r shouldBe a [OrderRejected]
    }

    "reject duplicate" in {
      val store = new InMemoryOrderStore()
      val repo = new OrderRepository(store)
      Await.result(repo.create("o-z", "c-1", 10.0), 5.seconds)
      val r = Await.result(repo.create("o-z", "c-1", 20.0), 5.seconds)
      r shouldBe a [OrderRejected]
    }
  }
}
