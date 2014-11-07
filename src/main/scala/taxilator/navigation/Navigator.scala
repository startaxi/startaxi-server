package taxilator.navigation

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.TimerBasedThrottler
import startaxi.Settings
import taxilator.Units.Coords

object Navigator {
  case class NavigationRequest(from: Coords, to: Coords, receiver: ActorRef)

  def props = Props(new Navigator)
}

class Navigator extends Actor {

  import taxilator.navigation.Navigator._

  val throttler = context.system.actorOf(Props(classOf[TimerBasedThrottler], Settings.Navigation.rate))
  throttler ! SetTarget(Some(context.actorOf(Props(Settings.Navigation.provider))))

  def receive = {
    case (from: Coords, to: Coords) => throttler ! NavigationRequest(from, to, sender())
  }
}
