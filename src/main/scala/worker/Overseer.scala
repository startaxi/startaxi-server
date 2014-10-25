package worker

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import spray.routing.RequestContext
import taxilator.Taxi
import taxilator.Taxi.Client
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

  case class TaxiInstance(ref: ActorRef, andThen: Option[Coords], client: Option[Client], coords: Coords)
  case class OrderInProgress(ref: ActorRef, order: Order)
}

class Overseer extends Actor {

  import Overseer._
  import spray.httpx.SprayJsonSupport._
  import web.TaxiServiceJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  var taxiMap = Map[StaticProviderData, Seq[TaxiInstance]]()
  var inflightTaxis = Map[Int, OrderInProgress]()
  var completedOrders = Map[Int, Order]()

  def receive = {
    case Position(ref, andThen, client, provider, lon, lat) =>
      val providerSet = taxiMap.getOrElse(provider, Seq()).filterNot(_.ref == ref) :+ TaxiInstance(ref, andThen, client, Coords(lat = lat, lon = lon))
      taxiMap += (provider -> providerSet)

    case ClientDelivered =>
      val completed = inflightTaxis.collect {
        case (id, order) if order.ref == sender() => (id, order)
      }.headOption

      completed.fold() { case (id, order) =>
        completedOrders += (id -> order.order)
        inflightTaxis -= id
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

    case (ctx: RequestContext, order @ Order(providerId, userPosition, destination)) =>
      val providerTaxis = taxisInOrderTo(userPosition.coordinates).filterKeys(_.id == providerId).values.head
      val taxiToOrder = providerTaxis.filterNot(taxi => inflightTaxis.values.exists(_.ref == taxi.ref)).headOption

      taxiToOrder.fold(ctx.complete(Error.NoTaxiFromProvider(providerId))) { taxi =>
        val (_, arrivalEta) = distanceAndTime(taxi.coords, userPosition.coordinates.asCoords)
        val arrival = Arrival(
          (math.random * 1000).toInt,
          Coordinates(lat = taxi.coords.lat, lon = taxi.coords.lon),
          arrivalEta, pickedUp = false, arrived = false
        )
        taxi.ref ! PickupAndThen(userPosition.coordinates.asCoords, destination.coordinates.asCoords)
        inflightTaxis += (arrival.orderId -> OrderInProgress(taxi.ref, order))
        ctx.complete(arrival)
      }

    case (ctx: RequestContext, orderId: Int) =>
      val completedOrder = completedOrders.get(orderId).map(Left.apply)
      val taxiAndOrderInProgress = taxiAndOrderById(orderId).map(Right.apply)

      completedOrder.orElse(taxiAndOrderInProgress) match {
        case Some(Right((taxi, order))) =>
          val (arrivalEta, pickedUp) = taxi.andThen match {
            case Some(_) => distanceAndTime(taxi.coords, order.order.userPosition.coordinates.asCoords)._2 -> false
            case None => distanceAndTime(taxi.coords, order.order.destination.coordinates.asCoords)._2 -> true
          }
          val arrival = Arrival(orderId, Coordinates(lat = taxi.coords.lat, lon = taxi.coords.lon), arrivalEta, pickedUp, arrived = false)
          ctx.complete(arrival)

        case Some(Left(order)) =>
          val arrival = Arrival(orderId, order.userPosition.coordinates, arrivalEta = 0, pickedUp = true, arrived = true)
          ctx.complete(arrival)

        case None => ctx.complete(Error.NoOrderFound(orderId))
      }

    case (ctx: RequestContext, preferences: Preferences) =>
      taxiAndOrderById(preferences.orderId).fold(ctx.complete(Error.NoOrderFound(preferences.orderId))) { taxiAndOrder =>
        ctx.complete(DriverMessage("Biški nekanalina į Tauro kalną. Tuoj būsiu."))
      }
  }

  def taxiAndOrderById(orderId: Int) =
    inflightTaxis.get(orderId) flatMap { inProgressOrder =>
      taxiMap.filterKeys(_.id == inProgressOrder.order.providerId).values.head.find(_.ref == inProgressOrder.ref).map(_ -> inProgressOrder)
    }

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
