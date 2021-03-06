package taxilator.navigation

import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorContext
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
import spray.json.DefaultJsonProtocol
import taxilator.Taxi.Route
import taxilator.Units.Coords
import taxilator.Units.RichVector2Udegree
import taxilator.navigation.Navigator.NavigationRequest

object YourNavigationJsonProtocol extends DefaultJsonProtocol {

  case class GeojsonResult(coordinates: List[Seq[Double]], properties: Properties)
  case class Properties(distance: String, traveltime: String)

  implicit val propertiesFormat = jsonFormat2(Properties)
  implicit val geojsonResult = jsonFormat2(GeojsonResult)
}

object YourNavigation {
  def props = Props(new YourNavigation())
}

class YourNavigation extends Actor {

  import context.dispatcher

  def receive = {
    case NavigationRequest(from, to, receiver) => resolve(from, to) pipeTo receiver
  }

  def resolve(from: Coords, to: Coords)(implicit context: ActorContext) = {

    import YourNavigationJsonProtocol._
    import spray.httpx.SprayJsonSupport._

    val pipeline = {
      import context.system
      import scala.concurrent.duration._

      implicit val timeout = Timeout(60.seconds)
      sendReceive(IO(UHttp)) ~> unmarshal[GeojsonResult]
    }

    pipeline {
      val query = Query(
        "format" -> "geojson",
        "flat" -> from.lat.toString,
        "flon" -> from.lon.toString,
        "tlat" -> to.lat.toString,
        "tlon" -> to.lon.toString,
        "v" -> "motorcar",
        "fast" -> "1",
        "layer" -> "mapnik"
      )
      Get(Uri("http://www.yournavigation.org/api/1.0/gosmore.php").copy(query = query))
    }.map { result =>
      Route(
        result.coordinates.map(seq => Coords(seq.head, seq.last)),
        result.properties.distance.toDouble,
        result.properties.traveltime.toLong
      )
    }
  }
}
