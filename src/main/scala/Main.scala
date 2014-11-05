package startaxi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.can.server.UHttp
import taxilator.Taxi
import taxilator.Taxi.StaticProviderData
import taxilator.navigation.Navigator
import web.AssetsService
import web.TaxiPublisher
import web.TaxiService
import web.WsServer.WebSocketServer
import web.WsServer.WebSocketWorker
import worker.Overseer

import scala.io.StdIn

object Settings {
  val config = ConfigFactory.load().getConfig("startaxi")

  val taxiCount = config.getInt("taxi-count")

  val googleDirectionsApiKey = config.getString("google-directions-api-key")
}

object Main {

  trait OverseerAware {
    def overseer: ActorRef
  }

  class StartaxiService(serverConnection: ActorRef, override val overseer: ActorRef)
    extends WebSocketWorker(serverConnection)
    with OverseerAware
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
    val overseer = system.actorOf(Overseer.props)
    val workerProps = (conn: ActorRef) => Props(new StartaxiService(conn, overseer))
    val server = system.actorOf(WebSocketServer.props(workerProps), "websocket")
    val navigator = system.actorOf(Navigator.props, "navigator")

    val providers = Seq(
      StaticProviderData("blue-taxi", "Blue Taxi", 0.69, "#194069"),
      StaticProviderData("red-taxi", "Red Taxi", 0.49, "#890030")
    )

    for {
      (provider, providerIndex) <- providers.zipWithIndex
      index <- 0 until Settings.taxiCount
    } {
      system.actorOf(Taxi.props(provider, navigator), s"taxi-${provider.id}-$index")
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
