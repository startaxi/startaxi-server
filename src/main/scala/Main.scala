package startaxi

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import taxilator.Taxi
import web.WsServer.{WebSocketServer, WebSocketWorker}
import web.{AssetsService, TaxiPublisher, TaxiService}

import scala.io.StdIn

object Main {

  class StartaxiService(serverConnection: ActorRef)
    extends WebSocketWorker(serverConnection)
    with AssetsService
    with TaxiPublisher
    with TaxiService
  {

    def businessLogic: Receive = taxiPublisher
    def businessLogicNoUpgrade: Receive = runRoute {
      assets ~
        pathPrefix("api") {
          pathPrefix("taxi") {
            taxiService
          }
        }
    }

  }

  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    val workerProps = (conn: ActorRef) => Props(new StartaxiService(conn))
    val server = system.actorOf(WebSocketServer.props(workerProps), "websocket")

    Props(new Taxi())

    (0 until 10).foreach { i =>
      system.actorOf(Props[Taxi], s"taxi-$i")
    }

    val port = sys.env.getOrElse("PORT", "8080").toInt
    IO(UHttp) ! Http.Bind(server, "0.0.0.0", port)

    if (port == 8080) {
      // running from dev machine, support shutdown
      StdIn.readLine()
      system.shutdown()
      system.awaitTermination()
    }
  }


}
