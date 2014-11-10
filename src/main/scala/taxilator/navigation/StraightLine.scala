package taxilator.navigation

import akka.actor.Actor
import akka.pattern.pipe
import taxilator.navigation.Navigator.NavigationRequest
import scala.concurrent.Future
import taxilator.Units._
import io.github.karols.units._
import taxilator.Taxi.Route

class StraightLine extends Actor {

  import context.dispatcher

  def receive = {
    case NavigationRequest(from, _, receiver) => resolve(from) pipeTo receiver
  }

  def resolve(from: Coords) = {
    val deviation = (if (math.random > 0.5) (0.0, 0.0001) else (0.0001, 0.0)).of[degree]
    val path = for {
      i <- (1 to 20)
    } yield from + (i.toDouble * deviation)

    Future.successful(Route(path.toList, 0, 0))
  }

}
