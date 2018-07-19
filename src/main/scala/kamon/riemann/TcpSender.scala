package kamon.riemann

import java.awt.Event
import java.util.concurrent.TimeUnit
import java.io.IOException

import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Msg
import com.aphyr.riemann.client.RiemannClient
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async.{async, await}

class TcpSender(val hostName: String, val port: Int) (implicit protected val executionContext: ExecutionContext) extends SenderTrait {
  private val log = LoggerFactory.getLogger(getClass)
  var client = RiemannClient.tcp(hostName, port)

  override def connect(): Unit = {
    client.connect()
  }

  def asyncSend(events: Seq[Proto.Event]): Future[Unit] = {
    Future {
      events.grouped(10).foreach {
        batch =>
          log.debug(s"Sending batch with <${batch.length}> events")
          client.sendEvents(batch.asJava).deref()
      }
    }
  }


  override def send(events: Seq[Proto.Event], timeout: Duration = Duration.Inf): Future[Unit] =  {
    log.debug(s"Sending <${events.length}> events")
    log.debug("Sending message via TCP")

    val sent_msg = asyncSend(events)
    sent_msg.recover {
      case io: IOException => log.error("Io Exception", io)
      case runtime: RuntimeException => log.error("Runtime Exception", runtime)
    }
  }


  override def close(): Unit = {
    client.close()
  }

}

