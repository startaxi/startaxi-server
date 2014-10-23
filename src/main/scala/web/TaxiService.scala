package web

import spray.http.{ContentTypes, HttpEntity, HttpResponse}
import spray.json.DefaultJsonProtocol
import spray.routing.{HttpService, RejectionHandler}

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

trait TaxiService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
  import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
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

  val provider = path("provider") {
      get {
        entity(as[UserPosition]) { userPosition =>
          complete {
            val resp = Provider(1, "SmartTaxi", 12.0, 300)
            Seq(resp)
          }
        }
      }
    }

  val taxiService = provider

}
