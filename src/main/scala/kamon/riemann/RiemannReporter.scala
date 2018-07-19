
package kamon.riemann

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.riemann.RiemannReporter.Configuration
import kamon.util.DynamicAccess
import kamon.{Kamon, MetricReporter}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class RiemannReporter (implicit protected val executionContext: ExecutionContext) extends MetricReporter {

  private val log = LoggerFactory.getLogger(getClass)
  private var configuration: Option[Configuration] = None
  private var mapper: Option[MetricsMapper] = None
  private var sender : Option[TcpSender] = None

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    log.debug(s"Snapshot Received: <$snapshot>")
    mapper.foreach {
      metricMapper =>
        val events = metricMapper.toEvents(snapshot.metrics)
        sender.get.send(events)
    }
  }

  override def start(): Unit = {
    log.info("Starting Kamon Riemann Reporter")
    val config = Kamon.config
    configuration = Some(readConfiguration(config))
    restart(config)
  }

  private def restart(config: Config): Unit = {
    mapper = Some(getMapper(configuration.get.metricsMapper, config, Kamon.environment.host))
    sender.foreach(_.close())
    sender = Some(new TcpSender(configuration.get.hostname, configuration.get.port))
    sender.get.connect()
  }

  override def stop(): Unit = {
    sender.get.close()
    log.info("Stopping Kamon Riemann Reporter")
  }

  override def reconfigure(config: Config): Unit = {
    configuration = Some(readConfiguration(config))
    restart(config)
    log.info("The configuration was reloaded successfully.")
  }

  private def readConfiguration(config: Config): Configuration = {
    log.info("Reconfiguring")
    val riemannConfig = config.getConfig("kamon.riemann")
    val hostname = riemannConfig.getString("hostname")
    val port = riemannConfig.getInt("port")
    val metricsMapper = riemannConfig.getString("metrics-mapper")

    val parsedConfiguration = Configuration(hostname, port, metricsMapper)
    log.info(s"Configuration: <$parsedConfiguration>")
    parsedConfiguration
  }

  private def getMapper(keyMapperFQCN: String, config: Config, defaultHostname: String): MetricsMapper = {
    val args = immutable.Seq(classOf[Config] -> config, classOf[String] -> defaultHostname)
    val loader = new DynamicAccess(getClass.getClassLoader)
    loader.createInstanceFor[MetricsMapper](keyMapperFQCN, args).get
  }
}

object RiemannReporter {
  private case class Configuration(hostname: String, port: Int, metricsMapper: String)
}
