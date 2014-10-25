package worker

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import spray.routing.RequestContext
import taxilator.Taxi
import taxilator.Taxi.ClientDelivered
import taxilator.Taxi.PickupAndThen
import taxilator.Taxi.StaticProviderData
import taxilator.Taxi.Coords
import taxilator.Taxi.Position
import web.TaxiService.Arrival
import web.TaxiService.DriverMessage
import web.TaxiService.Error
import web.TaxiService.Estimate
import web.TaxiService.Order
import web.TaxiService.PossibleJourneys
import web.TaxiService.Coordinates
import web.TaxiService.Preferences
import web.TaxiService.Provider
import web.TaxiService.UserPosition

object Overseer {
  def props =
    Props(new Overseer())

  case class TaxiInstance(ref: ActorRef, coords: Coords)
}

class Overseer extends Actor {

  import Overseer._
  import spray.httpx.SprayJsonSupport._
  import web.TaxiServiceJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  var taxiMap = Map[StaticProviderData, Seq[TaxiInstance]]()
  var inflightArrivals = Set[(Arrival, ActorRef)]()

  def receive = {
    case Position(ref, _, provider, lon, lat) =>
      val providerSet = taxiMap.getOrElse(provider, Seq()).filterNot(_.ref == ref) :+ TaxiInstance(ref, Coords(lat = lat, lon = lon))
      taxiMap += (provider -> providerSet)

    case ClientDelivered =>
      inflightArrivals = inflightArrivals.filterNot {
        case (_, ref) if ref == sender() => true
        case _ => false
      }

    case (ctx: RequestContext, UserPosition(position)) =>

      val providers = closestTaxiTo(position) map { case (provider, taxi) =>
        val (_, arrivalEta) = distanceAndTime(taxi.coords, position.asCoords)
        Provider(provider.id, provider.name, provider.price, arrivalEta)
      }

      ctx.complete(providers)

    case (ctx: RequestContext, PossibleJourneys(userPosition, destinations)) =>

      val estimates = for {
        (provider, taxi) <- closestTaxiTo(userPosition.coordinates)
        destination <- destinations
      } yield {
        val (_, arrivalEta) = distanceAndTime(taxi.coords, userPosition.coordinates.asCoords)
        val (distanceInMeters, travelTimeInSeconds) =
          distanceAndTime(userPosition.coordinates.asCoords, destination.coordinates.asCoords)
        val price = provider.price / 1000 * distanceInMeters
        Estimate(
          Provider(provider.id, provider.name, provider.price, arrivalEta),
          destination, price, travelTimeInSeconds.toLong, distanceInMeters)
      }

      ctx.complete(estimates)

    case (ctx: RequestContext, Order(providerId, userPosition, destination)) =>
      val providerTaxis = taxisInOrderTo(userPosition.coordinates).filterKeys(_.id == providerId).values.head
      val taxiToOrder = providerTaxis.filterNot(taxi => inflightArrivals.map(_._2).contains(taxi.ref)).headOption

      taxiToOrder.fold(ctx.complete(Error.NoTaxiFromProvider(providerId))) { taxi =>
        val (_, arrivalEta) = distanceAndTime(taxi.coords, userPosition.coordinates.asCoords)
        val arrival = Arrival(
          (math.random * 1000).toInt,
          Coordinates(lat = taxi.coords.lat, lon = taxi.coords.lon),
          arrivalEta, arrived = false
        )
        taxi.ref ! PickupAndThen(userPosition.coordinates.asCoords, destination.coordinates.asCoords)
        inflightArrivals += ((arrival, taxi.ref))
        ctx.complete(arrival)
      }

    case (ctx: RequestContext, orderId: Int) =>
      arrivalByOrder(orderId).fold(ctx.complete(Error.NoOrderFound(orderId))) { arrival =>
        ctx.complete(arrival)
      }

    case (ctx: RequestContext, preferences: Preferences) =>
      arrivalByOrder(preferences.orderId).fold(ctx.complete(Error.NoOrderFound(preferences.orderId))) { arrival =>
        ctx.complete(DriverMessage("Biški nekanalina į Tauro kalną. Tuoj būsiu."))
      }
  }

  def arrivalByOrder(orderId: Int) =
    inflightArrivals.collect {
      case (arrival, _) if arrival.orderId == orderId => arrival
    }.headOption

  def taxisInOrderTo(destination: Coordinates) =
    taxiMap.mapValues(_.sortBy { taxi =>
      val (distance, _) = distanceAndTime(taxi.coords, destination.asCoords)
      distance
    })

  def closestTaxiTo(destination: Coordinates) =
    taxisInOrderTo(destination).mapValues(_.head)

  def distanceAndTime(from: Coords, to: Coords) = {
    val distanceMeters = (to - from).mag
    val arrivalEta = distanceMeters / (Taxi.CarSpeedMetersPerMs * 1000) // euristics
    (distanceMeters.toLong, arrivalEta.toLong)
  }
}
