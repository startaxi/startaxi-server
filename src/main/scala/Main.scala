package startaxi

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import taxilator.Taxi
import web.TaxiPublisher
import web.WsServer.WebSocketServer

object Main {
  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    val server = system.actorOf(WebSocketServer.props(TaxiPublisher.props()), "websocket")

    (0 to 10).foreach { i =>
      system.actorOf(Props[Taxi], s"taxi-$i")
    }

    IO(UHttp) ! Http.Bind(server, "0.0.0.0", sys.env.getOrElse("PORT", "8080").toInt)
  }
}
