package taxilator

import akka.actor.ActorContext
import akka.io.IO
import akka.util.Timeout
import spray.can.server.UHttp
import spray.client.pipelining._
import spray.http.Uri
import spray.http.Uri.Query
import spray.json.DefaultJsonProtocol
import spray.util._
import taxilator.Taxi.{Coords, Route}

object NavigatorJsonProtocol extends DefaultJsonProtocol {

  case class GeojsonResult(coordinates: List[Seq[Double]], properties: Properties)
  case class Properties(distance: String, traveltime: String)

  implicit val propertiesFormat = jsonFormat2(Properties)
  implicit val geojsonResult = jsonFormat2(GeojsonResult)
}

object Navigator {

  def resolve(from: Coords, to: Coords)(implicit context: ActorContext) = {

    import context.dispatcher
    import spray.httpx.SprayJsonSupport._
    import taxilator.NavigatorJsonProtocol._

import scala.concurrent.duration._
    implicit val timeout = Timeout(60.seconds)
    implicit val sys = actorSystem

    val pipeline = sendReceive(IO(UHttp)) ~> unmarshal[GeojsonResult]

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
