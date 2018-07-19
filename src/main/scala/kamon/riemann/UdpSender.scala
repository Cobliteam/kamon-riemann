package kamon.riemann

import java.util.concurrent.TimeUnit

import com.aphyr.riemann.Proto
import com.aphyr.riemann.Proto.Msg
import com.aphyr.riemann.client.RiemannClient
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class UdpSender (val hostname: String, val port: Int)(implicit protected val executionContext: ExecutionContext) extends SenderTrait {
  private val log = LoggerFactory.getLogger(getClass)
  val client = RiemannClient.udp(hostname, port)

  override def connect(): Unit ={
    client.connect()
  }

  override def send(events: Seq[Proto.Event], timeout: Duration = Duration.Inf ): Future[Unit] = {
    log.debug(s"Sending <${events.length}> events")
    Future {
      events.grouped(10).foreach {
        batch =>
          log.debug(s"Sending batch with <${batch.length}> events")
          log.debug("This is UDP!")
          val sent_msg = client.sendEvents(batch.asJava)
      }
    }
  }

  override def close(): Unit = {
    client.close()
  }
}
