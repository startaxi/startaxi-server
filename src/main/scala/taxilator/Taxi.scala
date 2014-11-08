package taxilator

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import com.github.nscala_time.time.Imports._
import io.github.karols.units._
import io.github.karols.units.defining._
import io.github.karols.units.SI._
import io.github.karols.units.jodaSupport._
import org.joda.time.DateTime
import org.joda.time.Duration
import taxilator.Units._

object Taxi {
  case object Wrum
  case object LunchBreakOver
  case object ClientDelivered
  case class Client(overseer: ActorRef)

  final val CarSpeed = 50.0.of[meter / second]
  final val TaxiTickEveryMs = 40

  case class Route(path: List[Coords], distance: Double, traveltime: Long)
  case class Position(ref: ActorRef, andThen: Option[Coords], client: Option[Client], provider: StaticProviderData, coords: Coords)
  case class StaticProviderData(id: String, name: String, price: Double, color: String)
  case class PickupAndThen(pickup: Coords, andThen: Coords)

  def props(provider: StaticProviderData, navigator: ActorRef) =
    Props(new Taxi(provider, navigator))
}

class Taxi(provider: Taxi.StaticProviderData, navigator: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher
  import taxilator.Taxi._

  val engine = {
    import scala.concurrent.duration.DurationInt
    context.system.scheduler.schedule(DurationInt(1).second, DurationInt(TaxiTickEveryMs).millis, self, Wrum)
  }

  override def postStop(): Unit = {
    engine.cancel()
  }

  // Lukiškių Square
  def receive = idle(Coords(lat = 54.688723, lon = 25.270403), None, None)

  def goToRandomLocation(position: Coords): Unit = {
    val destination = position.deviateBy(0.1.of[degree])
    log.debug(s"Will go to $destination")
    resolve(position, destination, None, None)
  }

  def resolve(position: Coords, destination: Coords, andThen: Option[Coords], client: Option[Client]): Unit = {
    navigator ! (position, destination)
    context.become(resolvingRoute(position, andThen, client))
  }

  val idle = (position: Coords, andThen: Option[Coords], client: Option[Client]) => {
    {
      import scala.concurrent.duration.DurationInt
      context.system.scheduler.scheduleOnce(DurationInt(10).seconds, self, LunchBreakOver)
    }
    {
      case LunchBreakOver => andThen match {
        case Some(coords) => resolve(position, coords, None, client)
        case None =>
          client.fold()(_.overseer ! ClientDelivered)
          goToRandomLocation(position)
      }
      case PickupAndThen(pickupFrom, newAndThen) => client match {
        case Some(Client(o)) => // neg
        case None => resolve(position, pickupFrom, Some(newAndThen), Some(Client(sender())))
      }
      case _ =>
        log.debug(s"idle at $position")
        publish(andThen, client, position)
    }: Receive
  }

  val resolvingRoute = (position: Coords, andThen: Option[Coords], client: Option[Client]) => {
    case Wrum =>
      log.debug(s"resolving")
      publish(andThen, client, position)
    case Status.Failure(f) =>
      log.info(s"Was unable to resolve $position. Got ${f.getMessage}. Trying again.")
      goToRandomLocation(position)
    case route: Route => route.path match {
      case Nil =>
        log.info(s"Got empty path when trying to move from $position")
        goToRandomLocation(position)
      case _ =>
        log.info(s"Resolved destination. Going from $position to ${route.path.last} in ${route.path.size} hops.")
        context.become(busy(position, route, DateTime.now, andThen, client))
    }
    case PickupAndThen(pickupFrom, newAndThen) => client match {
      case Some(Client(o)) => // neg
      case None => resolve(position, pickupFrom, Some(newAndThen), Some(Client(sender())))
    }
  }: Receive

  val busy: (Coords, Route, DateTime, Option[Coords], Option[Client]) => Receive =
    (position: Coords, route: Route, timestamp: DateTime, andThen: Option[Coords], client: Option[Client]) => {
    case Wrum =>
      implicit val taxiPlanet = Earth

      val now = DateTime.now
      val timeSince = (timestamp to now).millis.milli[second]

      val direction = (route.path.head - position).unit
      val projectedPos = position offsetBy (direction * CarSpeed * timeSince)

      val distanceToPath = (position distanceTo route.path.head).length
      val distanceToProjectedPos = (position distanceTo projectedPos).length

      val (newPos, newPath, timeLeft) =
        if (distanceToProjectedPos > distanceToPath) {
          self ! Wrum // need another Wrum to use the remaining time
          (route.path.head, route.path.tail, (distanceToProjectedPos - distanceToPath) / CarSpeed)
        } else
          (projectedPos, route.path, 0.0.of[second])

      log.debug(s"busybusy, direction: $direction, projectedPos: $projectedPos, timeLeft: $timeLeft, newPos: $newPos, millisSince: $timeSince, timestamp: $timestamp, distanceToPath: $distanceToPath, distanceToProjectedPos: $distanceToProjectedPos, newPathSize: ${newPath.size}")
      publish(andThen, client, newPos)

      newPath match {
        case Nil => context.become(idle(newPos, andThen, client))
        case _ => context.become(busy(newPos, route.copy(path = newPath), now.minusMillis(timeLeft.convert[millisecond].value.toInt), andThen, client))
      }
    case PickupAndThen(pickupFrom, newAndThen) => client match {
      case Some(Client(o)) => // neg
      case None => resolve(position, pickupFrom, Some(newAndThen), Some(Client(sender())))
    }
  }

  def publish(andThen: Option[Coords], client: Option[Client], position: Coords) =
    context.system.eventStream.publish(Position(self, andThen, client, provider, position))
}
