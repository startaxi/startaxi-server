package taxilator.navigation

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.io.IO
import akka.pattern.pipe
import akka.util.Timeout
import spray.can.server.UHttp
import spray.client.pipelining.Get
import spray.client.pipelining.WithTransformerConcatenation
import spray.client.pipelining.sendReceive
import spray.client.pipelining.unmarshal
import spray.http.Uri
import spray.http.Uri.Query
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import spray.json.SnakifiedSprayJsonSupport
import startaxi.Settings
import taxilator.Units.Coords
import taxilator.Units.RichVector2Udegree
import taxilator.navigation.Navigator.NavigationRequest

object GoogleDirectionsJsonProtocol extends SnakifiedSprayJsonSupport {

  case class GoogleDirectionsResult(routes: List[Route])
  case class Route(legs: List[Leg], overviewPolyline: Polyline)
  case class Polyline(points: String)
  case class Leg(distance: TextValue, duration: TextValue)
  case class TextValue(text: String, value: Long)

  implicit val textValueFormat = jsonFormat2(TextValue)
  implicit val legFormat = jsonFormat2(Leg)
  implicit val polylineFormat = jsonFormat1(Polyline)
  implicit val routeFormat = jsonFormat2(Route)
  implicit val googleDirectionsResultFormat = jsonFormat1(GoogleDirectionsResult)
}

object GoogleDirections {
  def props = Props(new GoogleDirections())
}

class GoogleDirections extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case NavigationRequest(from, to, receiver) => resolve(from, to) pipeTo receiver
  }

  def resolve(from: Coords, to: Coords) = {

    import spray.httpx.SprayJsonSupport._
    import taxilator.navigation.GoogleDirectionsJsonProtocol._

    val pipeline = {
      import context.system
      import scala.concurrent.duration._

      implicit val timeout = Timeout(60.seconds)
      sendReceive(IO(UHttp)) ~> unmarshal[GoogleDirectionsResult]
    }

    pipeline {
      val query = Query(
        "origin" -> s"${from.lat.toString},${from.lon.toString}",
        "destination" -> s"${to.lat.toString},${to.lon.toString}",
        "key" -> Settings.googleDirectionsApiKey
      )
      Get(Uri("https://maps.googleapis.com/maps/api/directions/json").copy(query = query))
    }.flatMap { result =>
      result.routes.headOption.map { route =>
        val path = gpc.Polyline.fromEncoding(route.overviewPolyline.points).map(ll => Coords(lat = ll.lat, lon = ll.lng)).toList
        val distance = route.legs.headOption.fold(0L)(_.distance.value)
        val traveltime = route.legs.headOption.fold(0L)(_.duration.value)

        log.info(s"Resolved route from $from to $to which is $distance meters long and will take $traveltime seconds with ${path.size} hops.")

        taxilator.Taxi.Route(path, distance, traveltime)
      }.map(Future.successful).getOrElse(Future.failed(new IllegalArgumentException(s"Was not able to resolve route between $from and $to.")))
    }
  }

}
