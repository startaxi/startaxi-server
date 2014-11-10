package startaxi

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationLong
import scala.io.StdIn

import com.typesafe.config.ConfigFactory

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.contrib.throttle.Throttler.Rate
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.routing.Directive.pimpApply
import taxilator.Taxi
import taxilator.Taxi.StaticProviderData
import taxilator.navigation.Navigator
import web.AssetsService
import web.TaxiPublisher
import web.TaxiService
import web.WsServer.WebSocketServer
import web.WsServer.WebSocketWorker
import worker.Overseer

object Settings {
  val config = ConfigFactory.load().getConfig("startaxi")

  val taxiCount = config.getInt("taxi-count")

  val googleDirectionsApiKey = config.getString("google-directions-api-key")

  object Navigation {
    val provider = Class.forName(config.getString("navigation.default.provider"))
    val rate = {
      val initialRate = Rate(config.getInt("navigation.default.requests"), config.getDuration("navigation.default.per", TimeUnit.MINUTES).minutes)
      val normalizedRate = Rate(1, (initialRate.durationInMillis() / initialRate.numberOfCalls).milliseconds)
      normalizedRate
    }
  }
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
    val overseer = system.actorOf(Overseer.props, "overseer")
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
