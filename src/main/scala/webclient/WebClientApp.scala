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

}

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorLogging, PoisonPill, ReceiveTimeout }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.{ Framing }
import akka.util.ByteString
import akka.util.Timeout

import scala.util.{ Failure, Success }

class ClientActor extends Actor with ActorLogging {

  val uri: Uri = "https://www.berlin.de" //"http://akka.io"

  import akka.pattern.pipe
  import context.dispatcher
  import scala.concurrent.duration._

  private final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  private val startTime = System.currentTimeMillis

  override def preStart() = {
    http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)
    context.setReceiveTimeout(Duration(20, TimeUnit.SECONDS))
    println(s"GET request $uri started.")
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      log.info("Getting page {} ...", uri)
      entity.dataBytes.
        //with framing according to http://doc.akka.io/docs/akka-http/10.0.9/scala/http/implications-of-streaming-http-entity.html#consuming-the-http-response-entity-client-
        via(Framing.delimiter(ByteString("href=\""), maximumFrameLength = 65000, allowTruncation = true)).
        runForeach {
          frame =>
            println("====================================Got response frame====================================\n" + frame.utf8String)
        }.onComplete {
          case Success(done) =>
            println(s"GET request $uri completed within $elapsedMillis millis.")
            _terminate()
          case Failure(t) =>
            log.error("GET request {} dataBytes read completed with Failure {} within {} millis.", uri, t, elapsedMillis)
            _terminate()
        }
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()
      _terminate()

    case ReceiveTimeout =>
      log.error("GET request {} was not answered within {} millis.", uri, elapsedMillis)
      _terminate()

    case unexpected =>
      log.error("GET request {} was answered by unexpected message {}", uri, unexpected)
      _terminate()
  }

  private def elapsedMillis = System.currentTimeMillis() - startTime

  private def _terminate(): Unit = {
    val system = context.system
    import system.dispatcher
    //Give the actor some time to terminate before terminating the actor system:
    system.scheduler.scheduleOnce(2.seconds) {
      log.info("Going to shut down the system...")
      http.shutdownAllConnectionPools()
      system.terminate()
    }
    self ! PoisonPill
  }

}
