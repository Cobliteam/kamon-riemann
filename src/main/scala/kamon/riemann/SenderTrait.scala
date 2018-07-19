
package kamon.riemann

import com.aphyr.riemann.Proto

import scala.concurrent.Future
import scala.concurrent.duration.Duration


trait SenderTrait {

  def connect() : Unit
  def send(events: Seq[Proto.Event], timeout: Duration = Duration.Inf): Future[Unit]
  def close() : Unit

}
