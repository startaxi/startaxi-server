package worker

import akka.actor.{Props, Actor}
import spray.routing.RequestContext
import taxilator.Taxi
import taxilator.Taxi.{Coords, Position}
import web.TaxiService.{Provider, Coordinates, UserPosition}

object Overseer {
  def props =
    Props(new Overseer())

  case class TaxiInstance(id: String, pricePerKm: Double, coords: Coords)
}

class Overseer extends Actor {

  import Overseer._

  context.system.eventStream.subscribe(self, classOf[Position])

  var taxiMap = Map[String, Set[TaxiInstance]]()
  var providerPrice = Map[String, Double]()

  def receive = {
    case Position(id, provider, pricePerKm, lon, lat) =>
      val providerSet = taxiMap.getOrElse(provider, Set()) + TaxiInstance(id, pricePerKm, Coords(lat = lat, lon = lon))
      taxiMap += (provider -> providerSet)
      providerPrice += (provider -> pricePerKm)

    case (ctx: RequestContext, UserPosition(Coordinates(lat, lon))) =>

      import spray.httpx.SprayJsonSupport._
      import web.TaxiServiceJsonProtocol._

      val providerArrivalEta = taxiMap.map { case (provider, taxiSet) =>
        val userCoords = Coords(lat = lat, lon = lon)
        val shortestEta = taxiSet.map(_.coords - userCoords mag).min / (Taxi.CarSpeedMetersPerMs * 1000) // euristics
        provider -> shortestEta.toLong // in seconds
      }

      val providers = taxiMap map { case (provider, _) =>
        Provider(provider.hashCode, provider, providerPrice(provider), providerArrivalEta(provider))
      }

      ctx.complete(providers)
  }
}
