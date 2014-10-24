package worker

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import spray.routing.RequestContext

object ProviderWorker {
  def props(ctx: RequestContext, overseer: ActorRef) = Props(new ProviderWorker(ctx, overseer))
}

class ProviderWorker(ctx: RequestContext, overseer: ActorRef) extends Actor {

  import web.TaxiService._

  def receive = {
    case position: UserPosition => overseer ! position
    //case providers: List[Provider] => ctx.complete(Seq(Provider(1, "Taxi", 12.0, 300)))
  }
}
