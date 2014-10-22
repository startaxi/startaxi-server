package web

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

object WsServer {

  object WebSocketServer {
    def props(workerProps: ActorRef => Props) = Props(classOf[WebSocketServer], workerProps)
  }
  class WebSocketServer(workerProps: ActorRef => Props) extends Actor with ActorLogging {
    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        val serverConnection = sender()
        val conn = context.actorOf(workerProps(serverConnection))
        serverConnection ! Http.Register(conn)
    }
  }

  object WebSocketWorker {
    def props(serverConnection: ActorRef) = Props(classOf[WebSocketWorker], serverConnection)
  }
  abstract class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {
    override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

    def businessLogic: Receive

    def businessLogicNoUpgrade: Receive = {
      implicit val refFactory: ActorRefFactory = context
      runRoute {
        path("") {
          getFromResource("webapp/index.html")
        } ~ getFromResourceDirectory("webapp")
      }
    }
  }

}

