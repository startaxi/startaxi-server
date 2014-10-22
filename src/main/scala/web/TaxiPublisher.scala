package web

import akka.actor.{ActorRef, Props}
import spray.can.websocket.frame.TextFrame
import spray.json._
import taxilator.Taxi.Position
import web.WsServer.WebSocketWorker

object TaxiPublisherJsonProtocol extends DefaultJsonProtocol {
  implicit val positionFormat = jsonFormat3(Position)
}

object TaxiPublisher {
  def props() =
    (serverConnection: ActorRef) => Props(classOf[TaxiPublisher], serverConnection)
}

class TaxiPublisher(serverConnection: ActorRef) extends WebSocketWorker(serverConnection) {

  import TaxiPublisherJsonProtocol._

  context.system.eventStream.subscribe(self, classOf[Position])

  def businessLogic = {
    case pos: Position => send(TextFrame(pos.toJson.toString))
  }

}
