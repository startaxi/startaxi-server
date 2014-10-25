package web

import akka.actor.{Actor, ActorRef, Props}
import spray.can.websocket.frame.TextFrame
import spray.json._
import taxilator.Taxi
import taxilator.Taxi.Position
import taxilator.Taxi.StaticProviderData
import web.WsServer.WebSocketWorker

object TaxiPublisherJsonProtocol extends DefaultJsonProtocol {
  implicit val webPositionFormat = jsonFormat5(WebPosition)

  case class WebPosition(id: String, occupied: Boolean, lat: Double, lon: Double, color: String)
}

object TaxiPublisher {
  case object Publish
}

trait TaxiPublisher { this: WebSocketWorker =>

  import TaxiPublisher._
  import TaxiPublisherJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])
  val publish = {
    import scala.concurrent.duration._
    import context.dispatcher
    context.system.scheduler.schedule(0.seconds, Taxi.TaxiTickEveryMs.millis, self, Publish)
  }

  var positionToPublish = List[WebPosition]()

  override def postStop(): Unit = {
    publish.cancel()
  }

  def taxiPublisher: Receive = {
    case Position(ref, _, client, StaticProviderData(_, _, _, color), lon, lat) =>
      positionToPublish :+= WebPosition(ref.toString, client.fold(false)(_ => true), lat, lon, color)
    case Publish =>
      send(TextFrame(positionToPublish.toJson.toString))
      positionToPublish = List[WebPosition]()
  }

}
