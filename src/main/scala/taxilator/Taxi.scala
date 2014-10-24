package taxilator

import akka.actor.{Props, ActorLogging, Actor}
import akka.pattern.pipe
import org.joda.time.DateTime

import com.github.nscala_time.time.Imports._

object Taxi {
  case object Wrum
  case object LunchBreakOver

  case class CartVector(x: Double, y: Double) {
    def norm: CartVector = mag match {
      case 0.0 => CartVector(1, 0)
      case _ => this * (1 / mag)
    }
    def mag: Double = math.sqrt(x * x + y * y)
    def *(factor: Double): CartVector = CartVector(x * factor, y * factor)
  }
  case class Coords(lon: Double, lat: Double) {

    final val EarthRadius = 6371000 // meters

    def -(that: Coords): CartVector = CartVector(
      (lon - that.lon).toRadians * r(lat),
      (lat - that.lat).toRadians * EarthRadius
    )
    def +(that: CartVector): Coords = Coords(
      lon + (that.x / r(lat)).toDegrees,
      lat + (that.y / EarthRadius).toDegrees
    )

    private def r(latRad: Double) = EarthRadius * math.cos(latRad)
  }
  case class Route(path: List[Coords], distance: Double, traveltime: Long)
  case class Position(id: String, lon: Double, lat: Double)

  def props(id: Int, name: String, price: Double) =
    Props(new Taxi(id, name, price))
}

class Taxi(id: Int, name: String, price: Double) extends Actor with ActorLogging {

  import Taxi._
  import context.dispatcher

  final val CarSpeed = 50.0 / 1000 // meters per ms

  val engine = {
    import scala.concurrent.duration.DurationInt
    context.system.scheduler.schedule(DurationInt(1).second, DurationInt(40).millis, self, Wrum)
  }

  override def postStop: Unit = {
    engine.cancel()
  }

  // Lukiškių Square
  def receive = idle(Coords(lon = 25.270403, lat = 54.688723))

  val idle = (position: Coords) => {
    {
      import scala.concurrent.duration.DurationInt
      context.system.scheduler.scheduleOnce(DurationInt(10).seconds, self, LunchBreakOver)
    }
    {
      case LunchBreakOver =>
        val destination = randomLocation(position)
        log.debug(s"will go to $destination")
        Navigator.resolve(position, destination) pipeTo self
        context.become(resolvingRoute(position))
      case _ => log.debug(s"idle at $position")
    }: Receive
  }

  val resolvingRoute = (position: Coords) => {
    case Wrum => log.debug(s"resolving")
    case route: Route => context.become(busy(position, route, DateTime.now))
  }: Receive

  val busy: (Coords, Route, DateTime) => Receive = (position: Coords, route: Route, timestamp: DateTime) => {
    case Wrum =>
      val now = DateTime.now
      val millisSince = (timestamp to now).millis

      val direction = (route.path.head - position).norm
      val projectedPos = position + (direction * CarSpeed * millisSince)

      val distanceToPath = (position - route.path.head).mag
      val distanceToProjectedPos = (position - projectedPos).mag

      val (newPos, newPath, timeLeft) =
        if (distanceToProjectedPos > distanceToPath) {
          self ! Wrum // need another Wrum to use the remaining time
          (route.path.head, route.path.tail, (distanceToProjectedPos - distanceToPath) / CarSpeed)
        } else
          (projectedPos, route.path, 0.0)

      log.debug(s"busybusy, timeLeft: $timeLeft, newPos: $newPos, head: ${route.path.head}, dropped: ${route.path.size - newPath.size}, newPathSize: ${newPath.size}")
      context.system.eventStream.publish(Position(self.toString, newPos.lon, newPos.lat))

      newPath match {
        case Nil => context.become(idle(newPos))
        case _ => context.become(busy(newPos, route.copy(path = newPath), now - timeLeft.toLong.toDuration.millis))
      }
  }

  def randomLocation(point: Coords) = {
    def randomDeviation =
      (math.random - 0.5) / 10
    Coords(lon = point.lon + randomDeviation, lat = point.lat + randomDeviation)
  }
}
