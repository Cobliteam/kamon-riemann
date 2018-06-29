/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.riemann

import com.aphyr.riemann.Proto.{Attribute, Event}
import com.typesafe.config.Config
import kamon.Tags
import kamon.metric.MeasurementUnit.Dimension
import kamon.metric._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


class DefaultMetricsMapper(config: Config, defaultHost: String) extends MetricsMapper {

  import DefaultMetricsMapper._

  private val log = LoggerFactory.getLogger(getClass)
  val configSettings = config.getConfig("kamon.riemann")

  val customHostTag = {
    if (! configSettings.hasPath("metric-mapping.host")) {
      None
    } else {
      Option(configSettings.getString("metric-mapping.host"))
    }
  }
  val serviceParts = parseServiceTemplate(configSettings.getString("service"))
  val ttl = configSettings.getDouble("default-ttl").toFloat
  val percentiles = configSettings.getIntList("percentiles")

  private def parseServiceTemplate(template: String): Seq[ServicePart] = {
    val pattern = """<([\w-]+)(?::([\w-]*))?>"""r
    val matches = pattern.findAllMatchIn(template)
    val (end, parts) = matches.foldLeft(0 -> Seq.empty[ServicePart]) {
      case ((pos, parts), m) =>
        m.end -> (
          parts :+ Constant(template.substring(pos, m.start)) :+ PlaceHolder(m.group(1), Option(m.group(2))))
    }
    parts :+ Constant(template.substring(end))
  }

  private def getService(possibleValues: Tags): Either[String, String] = {
    Right(
      serviceParts.map{
        case Constant(value) => value
        case PlaceHolder(key, Some(defaultValue)) => possibleValues.getOrElse(key, defaultValue)
        case PlaceHolder(key, _) => possibleValues.getOrElse(key, return Left(key))
      }.mkString)
  }

  override def toEvents(snapshot: MetricsSnapshot): Seq[Event] = {
    val counters = snapshot.counters.flatMap(parseMetricValue)
    val gauges = snapshot.gauges.flatMap(parseMetricValue)
    val histograms = snapshot.histograms.flatMap(parseMetricDistribution)
    val rangeSamples = snapshot.rangeSamplers.flatMap(parseMetricDistribution)


    counters ++ gauges ++ histograms ++ rangeSamples
  }

  private def generateEvent(name: String, value: Double, extraTags: Tags): Option[Event] = {
    val hostToUse = customHostTag.flatMap(extraTags.get).getOrElse(defaultHost)

    val possibleValues = extraTags + ("host" -> hostToUse, "name" -> name)

    getService(possibleValues) match {
      case Left(missingKey) =>
        log.warn(s"Event <$name> is missing tag <$missingKey> without a default value. Discarding it.")
        None
      case Right(service) =>
        val b = Event.newBuilder
        val attributes = extraTags.foldLeft(Seq.empty[Attribute]) {

          case (acc, (key, value)) =>
            Attribute.newBuilder().setKey(key).setValue(value).build() +: acc
        }

        b.setService(service)

        b.setHost(hostToUse)
        b.setTtl(ttl)
        b.setMetricD(value)
        b.addAllAttributes(attributes.asJava)
        Some(b.build)
    }
  }

  private def parseMetricValue(metric: MetricValue): Option[Event] = {
    val name = metric.name

    val convertedUnit = metric.unit.dimension match {
      case Dimension.Time => MeasurementUnit.time.milliseconds
      case Dimension.Information => MeasurementUnit.information.bytes
      case Dimension.Percentage | Dimension.None => metric.unit
    }

    val value = MeasurementUnit.scale(metric.value, metric.unit, convertedUnit)
    val tags = metric.tags + ("unit" -> convertedUnit.magnitude.name)

    generateEvent(name, value, tags)
  }

  private def parseMetricDistribution(distribution: MetricDistribution): Seq[Event] = {
    val name = distribution.name
    val unit = distribution.unit

    val convertedUnit = distribution.unit.dimension match {
      case Dimension.Time => MeasurementUnit.time.milliseconds
      case Dimension.Information => MeasurementUnit.information.bytes
      case Dimension.Percentage | Dimension.None => unit
    }
    val tags = distribution.tags + ("unit" -> convertedUnit.magnitude.name)

    val min = MeasurementUnit.scale(distribution.distribution.min, unit, convertedUnit)
    val max = MeasurementUnit.scale(distribution.distribution.max, unit, convertedUnit)
    val sum = MeasurementUnit.scale(distribution.distribution.sum, unit, convertedUnit)
    val count =  distribution.distribution.count
    val mean = if (count == 0) { 0 } else { sum / count }

    val events = Seq.newBuilder[Option[Event]]

    events += generateEvent(name + ".max", max, tags)
    events += generateEvent(name + ".min", min, tags)
    events += generateEvent(name + ".mean", mean, tags)
    distribution.distribution.percentiles.foreach(
      percentile => {
        if (percentiles.contains(percentile.quantile.toInt)) {
          val value = MeasurementUnit.scale(percentile.value, unit, convertedUnit)
          events += generateEvent(name + s".p${percentile.quantile}", value, tags)
        }
      }
    )
    events.result.flatten
  }
}

object DefaultMetricsMapper {
  sealed trait ServicePart
  case class Constant(value: String) extends ServicePart
  case class PlaceHolder(key: String, defaultValue: Option[String]) extends ServicePart
}

