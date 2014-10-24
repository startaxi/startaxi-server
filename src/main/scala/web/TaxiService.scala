package web

import akka.actor.Props
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.HttpResponse
import spray.json.DefaultJsonProtocol
import spray.routing.HttpService
import spray.routing.RejectionHandler
import startaxi.Main.OverseerAware

object TaxiService {
  case class Error(code: Int, message: String)

  case class Coordinates(lat: Long, lon: Long)
  case class UserPosition(coordinates: Coordinates)
  case class Provider(id: Int, name: String, pricePerKm: Double, arrivalEta: Long)
}

object TaxiServiceJsonProtocol extends DefaultJsonProtocol {
  import web.TaxiService._

  implicit val errorFormat = jsonFormat2(Error)

  implicit val coordinatesFormat = jsonFormat2(Coordinates)
  implicit val userPositionFormat = jsonFormat1(UserPosition)
  implicit val providerFormat = jsonFormat4(Provider)
}

trait TaxiService extends HttpService { self: OverseerAware =>

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
  import web.TaxiService._
  import web.TaxiServiceJsonProtocol._

  def jsonify(response: HttpResponse): HttpResponse = {
    import spray.json._
    val err = Error(code = response.status.intValue, message = response.entity.asString).toJson
    response.withEntity(HttpEntity(ContentTypes.`application/json`, err.toString))
  }

  implicit val apiRejectionHandler = RejectionHandler {
    case rejections => mapHttpResponse(jsonify) {
      RejectionHandler.Default(rejections)
    }
  }

  def spawnWorker(props: Props) =
    actorRefFactory.actorOf(props)

  val provider = path("provider") {
      get {
        entity(as[UserPosition]) { userPosition =>
          ctx => overseer ! (ctx, userPosition)
        }
      }
    }

  val taxiService = provider

}
