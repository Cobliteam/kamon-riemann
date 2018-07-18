package kamon.riemann

import java.awt.Event
import java.util.concurrent.TimeUnit

import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Msg
import com.aphyr.riemann.client.RiemannClient
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class TcpSender(val hostName: String, val port: Int) (implicit protected val executionContext: ExecutionContext) {
  private val log = LoggerFactory.getLogger(getClass)
  var client = RiemannClient.tcp(hostName, port)

  def connect(): Unit = {
    client.connect()
  }

  def send(events: Seq[Proto.Event], timeout: Duration = Duration.Inf): Future[Unit] = {
    log.debug(s"Sending <${events.length}> events")
    Future{
      events.grouped(10).foreach{
        batch =>
          log.debug(s"Sending batch of size <${batch.length}>")
          client.sendEvents(batch.asJava)
      }
    }
  }

  def close(): Unit = {
    client.close()
  }

}

