package worker

import akka.actor.{Props, Actor}
import spray.routing.RequestContext

object ProviderWorker {
  def props(ctx: RequestContext) = Props(new ProviderWorker(ctx))
}

class ProviderWorker(ctx: RequestContext) extends Actor {

  import spray.httpx.SprayJsonSupport._

  import web.TaxiService._
  import web.TaxiServiceJsonProtocol._

  def receive = {
    case p: UserPosition => ctx.complete(Seq(Provider(1, "Taxi", 12.0, 300)))
  }
}
