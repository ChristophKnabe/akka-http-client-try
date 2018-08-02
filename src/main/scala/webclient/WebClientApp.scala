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
  import java.net.{URL, URLConnection}
  import javax.net.ssl.SSLException

  def uriArg = "https://www.berlin.de/" //"http://akka.io"

  _adjustTrustStorePassword(uriArg)

  implicit val system: ActorSystem = ActorSystem("client-system")
  val clientActor = system.actorOf(Props[ClientActor], "client-actor")

  /**Set the Oracle default trust store password, if https access does not work. See https://github.com/ChristophKnabe/tool-instruction/blob/master/maven-trustAnchors-problem.md*/
  private def _adjustTrustStorePassword(uriArg: String): Unit = {
    val url = new URL(uriArg)
    if(url.getProtocol.equals("http")){ //unsecure
      return; //Does not need trustStore access.
    }
    val con: URLConnection = url.openConnection
    try{
      con.connect()
    }catch{
      case ssle: SSLException if ssle.getMessage == "java.lang.RuntimeException: Unexpected error: java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty" =>
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit")
      //Now https access should work!
    }
  }

}


import akka.actor.{Actor, ActorLogging, PoisonPill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{Uri, _}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.util.{Failure, Success}

class ClientActor extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher
  import scala.concurrent.duration._

  private val uri: Uri = WebClientApp.uriArg

  private final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  private val startTime = System.currentTimeMillis

  override def preStart(): Unit = {
    http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)
    log.info("Connecting to {} ...", uri.authority)
  }

  def receive: Actor.Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      log.info("Getting page {} ...", uri)
      entity.dataBytes.
        runForeach {
          chunk =>
            println("====================================Got response chunk====================================\n" + chunk.utf8String)
        }.onComplete {
          case Success(_) =>
            log.info("GET request {} succeeded within {} millis.", uri, elapsedMillis)
            _terminate()
          case Failure(t) =>
            log.error("GET request {} dataBytes read completed with Failure {} within {} millis.", uri, t, elapsedMillis)
            _terminate()
        }
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("GET request {} failed with response code {} within {} millis.", uri, code, elapsedMillis)
      resp.discardEntityBytes()
      _terminate()

    case unexpected =>
      log.error("GET request {} was answered by unexpected message {} within {} millis.", uri, unexpected, elapsedMillis)
      _terminate()
  }

  private def elapsedMillis = System.currentTimeMillis() - startTime

  private def _terminate(): Unit = {
    val system = context.system
    log.info("Shut down all HTTP connection pools...")
    for {
      _ <- http.shutdownAllConnectionPools()
      _ = self ! PoisonPill
    } yield system.scheduler.scheduleOnce(20.seconds) { //Give the connections double time of their idle timeout to be closed before terminating the actor system:
      log.info("Shut down the ActorSystem...")
      system.terminate()
    }
  }

}
