package taxilator.navigation

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.contrib.throttle.Throttler.RateInt
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.TimerBasedThrottler
import taxilator.Units.Coords

object Navigator {
  case class NavigationRequest(from: Coords, to: Coords, receiver: ActorRef)

  def props = Props(new Navigator)
}

class Navigator extends Actor {

  import taxilator.navigation.Navigator._

  val throttler = context.system.actorOf(Props(classOf[TimerBasedThrottler], 2.msgsPerSecond))
  throttler ! SetTarget(Some(context.actorOf(GoogleDirections.props)))

  def receive = {
    case (from: Coords, to: Coords) => throttler ! NavigationRequest(from, to, sender())
  }
}
