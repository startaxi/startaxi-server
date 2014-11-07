package web

import akka.actor.Props
import akka.actor.actorRef2Scala
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpResponse
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
import spray.json.DefaultJsonProtocol
import spray.json.pimpAny
import spray.routing.Directive.pimpApply
import spray.routing.HttpService
import spray.routing.RejectionHandler
import startaxi.Main.OverseerAware
import taxilator.Units.Coords

object TaxiService {
  object Error {
    val NoTaxiFromProvider = (providerId: String) =>
      Error(1, s"There is no available taxi from provider with id '$providerId'")
    val NoOrderFound = (orderId: Int) =>
      Error(2, s"There is no available order by id '$orderId'")
  }
  case class Error(code: Int, message: String)

  case class Coordinates(lat: Double, lon: Double) {
    def asCoords: Coords =
      Coords(lat = lat, lon = lon)
  }
  case class UserPosition(coordinates: Coordinates)
  case class Provider(id: String, name: String, pricePerKm: Double, arrivalEta: Long)

  case class Destination(id: Int, coordinates: Coordinates)
  case class PossibleJourneys(userPosition: UserPosition, destinations: List[Destination])
  case class Estimate(provider: Provider, destination: Destination, price: Double, travelTime: Long, distance: Long)

  case class Order(providerId: String, userPosition: UserPosition, destination: Destination)
  case class Arrival(orderId: Int, taxiPosition: Coordinates, arrivalEta: Long, pickedUp: Boolean, arrived: Boolean)

  case class Preferences(orderId: Int, radioStation: String, spotifyPlaylistId: Int, chatMessage: String)
  case class DriverMessage(chatMessage: String)
}

object TaxiServiceJsonProtocol extends DefaultJsonProtocol {
  import web.TaxiService._

  implicit val errorFormat = jsonFormat2(Error.apply)

  implicit val coordinatesFormat = jsonFormat2(Coordinates)
  implicit val userPositionFormat = jsonFormat1(UserPosition)
  implicit val providerFormat = jsonFormat4(Provider)

  implicit val destinationFormat = jsonFormat2(Destination)
  implicit val possibleJourneysFormat = jsonFormat2(PossibleJourneys)
  implicit val estimateFormat = jsonFormat5(Estimate)

  implicit val orderFormat = jsonFormat3(Order)
  implicit val arrivalFormat = jsonFormat5(Arrival)

  implicit val preferencesFormat = jsonFormat4(Preferences)
  implicit val driverMessageFormat = jsonFormat1(DriverMessage)
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

  val estimate = path("estimate") {
    get {
      entity(as[PossibleJourneys]) { possibleJourneys =>
        ctx => overseer ! (ctx, possibleJourneys)
      }
    }
  }

  val order =
    pathPrefix("order"/ IntNumber) { orderId =>
      pathEnd {
        get {
          ctx => overseer ! (ctx, orderId)
        }
      }
    } ~
    path("order") {
      post {
        entity(as[Order]) { order =>
          ctx => overseer ! (ctx, order)
        }
      } ~
      put {
        entity(as[Preferences]) { preferences =>
          ctx => overseer ! (ctx, preferences)
        }
      }
    }

  val taxiService = respondWithHeaders(
    RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept")
  ) {
    options {
      complete {
        StatusCodes.OK
      }
    } ~ provider ~ estimate ~ order
  }

}
