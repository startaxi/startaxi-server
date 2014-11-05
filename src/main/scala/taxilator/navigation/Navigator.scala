package taxilator.navigation

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.contrib.throttle.TimerBasedThrottler
import akka.contrib.throttle.Throttler._
import taxilator.Taxi.Coords
import taxilator.navigation.Navigator.NavigationRequest

object Navigator {
  case class NavigationRequest(from: Coords, to: Coords, receiver: ActorRef)

  def props = Props(new Navigator)
}

class Navigator extends Actor {

  val throttler = context.system.actorOf(Props(classOf[TimerBasedThrottler], 2.msgsPerSecond))
  throttler ! SetTarget(Some(context.actorOf(GoogleDirections.props)))

  def receive = {
    case (from: Coords, to: Coords) => throttler ! NavigationRequest(from, to, sender())
  }
}
