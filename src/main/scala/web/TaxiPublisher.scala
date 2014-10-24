package web

import akka.actor.{Actor, ActorRef, Props}
import spray.can.websocket.frame.TextFrame
import spray.json._
import taxilator.Taxi.Position
import taxilator.Taxi.StaticProviderData
import web.WsServer.WebSocketWorker

object TaxiPublisherJsonProtocol extends DefaultJsonProtocol {
  implicit val webPositionFormat = jsonFormat4(WebPosition)

  case class WebPosition(id: String, lat: Double, lon: Double, color: String)
}

trait TaxiPublisher { this: WebSocketWorker =>

  import TaxiPublisherJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  def taxiPublisher: Receive = {
    case Position(id, StaticProviderData(_, _, _, color), lon, lat) =>
      val webPosition = WebPosition(id, lat, lon, color)
      send(TextFrame(webPosition.toJson.toString))
  }

}
