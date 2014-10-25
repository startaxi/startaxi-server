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
import web.WsServer.WebSocketServer
import web.WsServer.WebSocketWorker
import web.AssetsService
import web.TaxiPublisher
import web.TaxiService
import worker.Overseer

import concurrent.Future
import scala.io.StdIn

object Settings {
  val config = ConfigFactory.load().getConfig("startaxi")

  val taxiCount = config.getInt("taxi-count")
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

    val providers = Seq(
      StaticProviderData("blue-taxi", "Blue Taxi", 0.69, "#194069"),
      StaticProviderData("red-taxi", "Red Taxi", 0.49, "#890030")
    )

    for {
      (provider, providerIndex) <- providers.zipWithIndex
      index <- 0 until Settings.taxiCount
    } {
      import scala.concurrent.duration._
      import scala.concurrent.ExecutionContext.Implicits.global

      val startTaxiAfter = (Settings.taxiCount * providerIndex + index) * 1000 // do not spam routing service
      akka.pattern.after(startTaxiAfter.millis, system.scheduler) { Future {
        system.actorOf(Taxi.props(provider), s"taxi-${provider.id}-$index")
      }}
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
