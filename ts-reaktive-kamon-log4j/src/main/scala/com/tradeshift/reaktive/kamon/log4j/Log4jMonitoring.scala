package com.tradeshift.reaktive.kamon.log4j

import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import kamon.Kamon
import akka.actor.ExtendedActorSystem
import akka.actor.Actor
import akka.cluster.Cluster
import scala.concurrent.duration._
import akka.actor.Extension
import akka.actor.Props
import akka.actor.DeadLetter
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.spi.LoggingEvent
import org.apache.log4j.Level

object Log4jMonitoring extends ExtensionId[Log4jMonitoringExtension] with ExtensionIdProvider  {
  override def createExtension(system: ExtendedActorSystem) = new Log4jMonitoringExtension
  override def lookup(): ExtensionId[_ <: Extension] = Log4jMonitoring  
}

class Log4jMonitoringExtension extends Kamon.Extension {
    Log4jMonitoringExtension.apply
}

object Log4jMonitoringExtension {
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)
  
  private lazy val apply: Unit = {
    // Do a log statement to force log4j and slf4j initialization
    log.debug("Log4J is now being monitored into Kamon")
    
    org.apache.log4j.Logger.getRootLogger().addAppender(new AppenderSkeleton {
      val metrics = Kamon.metrics.entity(Log4jMetrics, "global")
    
      override def close: Unit = {}
      override def requiresLayout = false

      override def append(event: LoggingEvent) = event.getLevel match {
        case Level.FATAL => metrics.fatal.increment()
        case Level.ERROR => metrics.error.increment()
        case Level.WARN => metrics.warn.increment()
        case _ =>
      }
    })    
  }
}
