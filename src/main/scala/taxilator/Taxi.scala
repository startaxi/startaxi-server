package taxilator

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.pattern.pipe
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import taxilator.navigation.{GoogleDirections, YourNavigation}

object Taxi {
  case object Wrum
  case object LunchBreakOver
  case object ClientDelivered
  case class Client(overseer: ActorRef)

  final val CarSpeedMetersPerMs = 50.0 / 1000 // meters per ms
  final val TaxiTickEveryMs = 40

  case class CartVector(x: Double, y: Double) {
    def norm: CartVector = mag match {
      case 0.0 => CartVector(1, 0)
      case _ => this * (1 / mag)
    }
    def mag: Double = math.sqrt(x * x + y * y)
    def *(factor: Double): CartVector = CartVector(x * factor, y * factor)
  }
  case class Coords(lat: Double, lon: Double) {

    final val EarthRadius = 6371000 // meters

    def -(that: Coords): CartVector = CartVector(
      (lon - that.lon).toRadians * r(lat),
      (lat - that.lat).toRadians * EarthRadius
    )
    def +(that: CartVector): Coords = Coords(
      lon = lon + (that.x / r(lat)).toDegrees,
      lat = lat + (that.y / EarthRadius).toDegrees
    )

    private def r(latRad: Double) = EarthRadius * math.cos(latRad)
  }
  case class Route(path: List[Coords], distance: Double, traveltime: Long)
  case class Position(ref: ActorRef, andThen: Option[Coords], client: Option[Client], provider: StaticProviderData, lon: Double, lat: Double)
  case class StaticProviderData(id: String, name: String, price: Double, color: String)
  case class PickupAndThen(pickup: Coords, andThen: Coords)

  def props(provider: StaticProviderData, navigator: ActorRef) =
    Props(new Taxi(provider, navigator))
}

class Taxi(provider: Taxi.StaticProviderData, navigator: ActorRef) extends Actor with ActorLogging {

  import Taxi._
  import context.dispatcher

  val engine = {
    import scala.concurrent.duration.DurationInt
    context.system.scheduler.schedule(DurationInt(1).second, DurationInt(TaxiTickEveryMs).millis, self, Wrum)
  }

  override def postStop(): Unit = {
    engine.cancel()
  }

  // Lukiškių Square
  def receive = idle(Coords(lon = 25.270403, lat = 54.688723), None, None)

  def goToRandomLocation(position: Coords): Unit = {
    val destination = randomLocation(position)
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
      case _ => log.debug(s"idle at $position")
    }: Receive
  }

  val resolvingRoute = (position: Coords, andThen: Option[Coords], client: Option[Client]) => {
    case Wrum => log.debug(s"resolving")
    case Status.Failure(f) =>
      log.info(s"Was unable to resolve $position. Got ${f.getMessage}. Trying again.")
      goToRandomLocation(position)
    case route: Route => route.path match {
      case Nil =>
        log.info(s"Got empty path when trying to move from $position")
        goToRandomLocation(position)
      case _ => context.become(busy(position, route, DateTime.now, andThen, client))
    }
    case PickupAndThen(pickupFrom, newAndThen) => client match {
      case Some(Client(o)) => // neg
      case None => resolve(position, pickupFrom, Some(newAndThen), Some(Client(sender())))
    }
  }: Receive

  val busy: (Coords, Route, DateTime, Option[Coords], Option[Client]) => Receive =
    (position: Coords, route: Route, timestamp: DateTime, andThen: Option[Coords], client: Option[Client]) => {
    case Wrum =>
      val now = DateTime.now
      val millisSince = (timestamp to now).millis

      val direction = (route.path.head - position).norm
      val projectedPos = position + (direction * CarSpeedMetersPerMs * millisSince)

      val distanceToPath = (position - route.path.head).mag
      val distanceToProjectedPos = (position - projectedPos).mag

      val (newPos, newPath, timeLeft) =
        if (distanceToProjectedPos > distanceToPath) {
          self ! Wrum // need another Wrum to use the remaining time
          (route.path.head, route.path.tail, (distanceToProjectedPos - distanceToPath) / CarSpeedMetersPerMs)
        } else
          (projectedPos, route.path, 0.0)

      log.debug(s"busybusy, direction: $direction, projectedPos: $projectedPos, timeLeft: $timeLeft, newPos: $newPos, millisSince: $millisSince, timestamp: $timestamp, distanceToPath: $distanceToPath, distanceToProjectedPos: $distanceToProjectedPos, newPathSize: ${newPath.size}")
      context.system.eventStream.publish(Position(self, andThen, client, provider, newPos.lon, newPos.lat))

      newPath match {
        case Nil => context.become(idle(newPos, andThen, client))
        case _ => context.become(busy(newPos, route.copy(path = newPath), now - timeLeft.toLong.toDuration.millis, andThen, client))
      }
    case PickupAndThen(pickupFrom, newAndThen) => client match {
      case Some(Client(o)) => // neg
      case None => resolve(position, pickupFrom, Some(newAndThen), Some(Client(sender())))
    }
  }

  def randomLocation(point: Coords) = {
    def randomDeviation =
      (math.random - 0.5) / 10
    Coords(lon = point.lon + randomDeviation, lat = point.lat + randomDeviation)
  }
}
