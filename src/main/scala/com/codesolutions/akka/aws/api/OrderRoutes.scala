package com.codesolutions.akka.aws.api

import com.codesolutions.akka.aws.domain.Order
import com.codesolutions.akka.aws.persistence.{OrderRepository, OrderResponse, OrderFound, OrderNotFound, OrderRejected}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json.{DefaultJsonProtocol, RootJsonFormat, deserializationError}

import java.time.Instant
import scala.concurrent.ExecutionContext

/**
 * HTTP layer for the AWS microservice.
 *
 * Endpoints:
 *   GET    /health         - liveness
 *   GET    /orders         - list (paged)
 *   POST   /orders         - create
 *   GET    /orders/:id     - read
 *   PUT    /orders/:id     - update status
 *
 * Maps to the JD's:
 *   - Designing well-defined RESTful APIs
 *   - High-availability, reliable solutions
 *   - Application architectural patterns (MVC, Microservices)
 */
class OrderRoutes(repo: OrderRepository)(implicit ec: ExecutionContext) {

  import JsonProtocols._

  def route: Route =
    path("health") {
      get { complete(StatusCodes.OK -> "ok") }
    } ~
    pathPrefix("orders") {
      pathEndOrSingleSlash {
        get {
          onSuccess(repo.list(50)) { orders =>
            complete(orders)
          }
        } ~
        post {
          entity(as[CreateOrderRequest]) { body =>
            onSuccess(repo.create(body.orderId, body.customerId, body.amount)) {
              case OrderFound(o)    => complete(StatusCodes.Created -> o)
              case OrderRejected(r) => complete(StatusCodes.BadRequest -> r)
              case OrderNotFound(_) => complete(StatusCodes.NotFound)
            }
          }
        }
      } ~
      path(Segment) { id =>
        get {
          onSuccess(repo.get(id)) {
            case OrderFound(o)    => complete(o)
            case OrderNotFound(_) => complete(StatusCodes.NotFound -> s"order $id not found")
            case OrderRejected(r) => complete(StatusCodes.BadRequest -> r)
          }
        } ~
        put {
          entity(as[UpdateOrderRequest]) { body =>
            onSuccess(repo.changeStatus(id, body.status)) {
              case OrderFound(o)    => complete(o)
              case OrderNotFound(_) => complete(StatusCodes.NotFound -> s"order $id not found")
              case OrderRejected(r) => complete(StatusCodes.BadRequest -> r)
            }
          }
        }
      }
    }
}

final case class CreateOrderRequest(orderId: String, customerId: String, amount: Double)
final case class UpdateOrderRequest(status: String)

object JsonProtocols extends DefaultJsonProtocol {
  implicit val instantFormat: RootJsonFormat[Instant] = new RootJsonFormat[Instant] {
    def write(i: Instant) = spray.json.JsString(i.toString)
    def read(json: spray.json.JsValue) = json match {
      case spray.json.JsString(s) => Instant.parse(s)
      case other => deserializationError("Instant expected as ISO-8601 string, got " + other)
    }
  }
  implicit val orderFormat: RootJsonFormat[Order] = jsonFormat5(Order)
  implicit val createReqFormat: RootJsonFormat[CreateOrderRequest] = jsonFormat3(CreateOrderRequest)
  implicit val updateReqFormat: RootJsonFormat[UpdateOrderRequest] = jsonFormat1(UpdateOrderRequest.apply _)
}
