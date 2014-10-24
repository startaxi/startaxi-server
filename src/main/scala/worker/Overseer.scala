package worker

import akka.actor.Actor
import akka.actor.Props
import spray.routing.RequestContext
import taxilator.Taxi
import taxilator.Taxi.StaticProviderData
import taxilator.Taxi.Coords
import taxilator.Taxi.Position
import web.TaxiService.Estimate
import web.TaxiService.PossibleJourneys
import web.TaxiService.Coordinates
import web.TaxiService.Provider
import web.TaxiService.UserPosition

object Overseer {
  def props =
    Props(new Overseer())

  case class TaxiInstance(id: String, coords: Coords)
}

class Overseer extends Actor {

  import Overseer._
  import spray.httpx.SprayJsonSupport._
  import web.TaxiServiceJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  var taxiMap = Map[StaticProviderData, Set[TaxiInstance]]()

  def receive = {
    case Position(id, provider, lon, lat) =>
      val providerSet = taxiMap.getOrElse(provider, Set()).filterNot(_.id == id) + TaxiInstance(id, Coords(lat = lat, lon = lon))
      taxiMap += (provider -> providerSet)

    case (ctx: RequestContext, UserPosition(coords)) =>

      val providers = providerArrivalEta(coords) map { case (provider, arrivalEta) =>
        Provider(provider.id.hashCode.abs, provider.name, provider.price, arrivalEta)
      }

      ctx.complete(providers)

    case (ctx: RequestContext, PossibleJourneys(userPosition, destinations)) =>

      val estimates = for {
        (provider, arrivalEta) <- providerArrivalEta(userPosition.coordinates)
        destination <- destinations
      } yield {
        val distanceInMeters = (userPosition.coordinates.asCoords - destination.coordinates.asCoords).mag.toLong
        val travelTimeInSeconds = distanceInMeters / (Taxi.CarSpeedMetersPerMs * 1000)
        val price = provider.price / 1000 * distanceInMeters
        Estimate(
          Provider(provider.id.hashCode.abs, provider.name, provider.price, arrivalEta),
          destination, price, travelTimeInSeconds.toLong, distanceInMeters)
      }

      ctx.complete(estimates)
  }

  def providerArrivalEta(coordinates: Coordinates) = {
    taxiMap.map { case (provider, taxiSet) =>
      val shortestEta = taxiSet.map(taxi => (taxi.coords - coordinates.asCoords).mag).min / (Taxi.CarSpeedMetersPerMs * 1000) // euristics
      provider -> shortestEta.toLong // in seconds
    }
  }
}
