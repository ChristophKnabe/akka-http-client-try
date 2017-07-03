package webclient

/**
 * Requests one web page by akka-http-core and prints its content by the akka-http-stream API.
 * Then smoothly terminates the ActorSystem.
 * Follows Akka HTTP 10.0.9 chapter "Consuming HTTP-based Services (Client-Side)", subchapter "Request-Level Client-Side API", subsubchapter "Future-Based Variant".
 * @see http://doc.akka.io/docs/akka-http/current/scala/http/client-side/request-level.html#using-the-future-based-api-in-actors
 * @author Christoph Knabe
 * @since 2017-07-03
 */
object WebClientApp extends App {

  import akka.actor.{ ActorSystem, Props }
  import scala.concurrent.{ Await }
  import scala.concurrent.duration.Duration

  implicit val system = ActorSystem("client-system")
  val clientActor = system.actorOf(Props[ClientActor], "client-actor")
  Await.result(system.whenTerminated, Duration.Inf)
  println("=============WebClientApp: Actor System Terminated=================")
}

import akka.actor.{ Actor, ActorLogging, PoisonPill }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }

class ClientActor extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher
  import scala.concurrent.duration._

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)
  val uri: Uri = "http://www.berlin.de" //"http://akka.io"

  override def preStart() = {
    http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      log.info("Getting page {} ...", uri)
      entity.dataBytes.runForeach {
        chunk =>
          log.info("====================================Got response chunk====================================\n" + chunk.utf8String)
      }.onComplete { done =>
        val system = context.system
        //Give the actor some time to terminate before terminating the actor system:
        system.scheduler.scheduleOnce(2.seconds) { system.terminate() }
        self ! PoisonPill
      }
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()
  }

}
