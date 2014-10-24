package web

import akka.actor.{Actor, ActorRef, Props}
import spray.can.websocket.frame.TextFrame
import spray.json._
import taxilator.Taxi.Position
import web.WsServer.WebSocketWorker

object TaxiPublisherJsonProtocol extends DefaultJsonProtocol {
  implicit val positionFormat = jsonFormat5(Position)
}

trait TaxiPublisher { this: WebSocketWorker =>

  import TaxiPublisherJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  def taxiPublisher: Receive = {
    case pos: Position => send(TextFrame(pos.toJson.toString))
  }

}
