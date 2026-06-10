package com.codesolutions.akka.aws

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.codesolutions.akka.aws.api.{JsonProtocols, OrderRoutes}
import com.codesolutions.akka.aws.persistence.OrderRepository
import com.codesolutions.akka.aws.store.InMemoryOrderStore
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

class OrderRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  import JsonProtocols._

  val store = new InMemoryOrderStore()
  store.seed()
  val repo = new OrderRepository(store)
  val routes = new OrderRoutes(repo).route

  "OrderRoutes" should {

    "GET /health returns 200" in {
      Get("/health") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "ok"
      }
    }

    "GET /orders lists seeded orders" in {
      Get("/orders") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include ("\"id\"")
      }
    }

    "POST /orders creates an order" in {
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = "/orders",
        entity = HttpEntity(ContentTypes.`application/json`,
          """{"orderId":"o-new","customerId":"c-9","amount":10.5}""")
      )
      req ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
    }

    "POST /orders rejects non-positive amount" in {
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = "/orders",
        entity = HttpEntity(ContentTypes.`application/json`,
          """{"orderId":"o-bad","customerId":"c","amount":0}""")
      )
      req ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "GET /orders/:id returns 200 when found" in {
      Get("/orders/o-1") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include ("\"id\":\"o-1\"")
      }
    }

    "GET /orders/:id returns 404 when not found" in {
      Get("/orders/does-not-exist") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "PUT /orders/:id updates status" in {
      Put("/orders/o-1", HttpEntity(ContentTypes.`application/json`,
        """{"orderId":"o-1","status":"PAID"}""")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include ("\"status\":\"PAID\"")
      }
    }
  }
}
