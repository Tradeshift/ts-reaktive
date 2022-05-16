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

object Log4jMonitoring {
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)

  def apply: Unit = {
    // Do a log statement to force log4j and slf4j initialization
    log.debug("Log4J is now being monitored into Kamon")

    org.apache.log4j.Logger.getRootLogger().addAppender(new AppenderSkeleton {
      val fatal = Kamon.counter("log4j.fatal").withoutTags()
      val error = Kamon.counter("log4j.error").withoutTags()
      val warn = Kamon.counter("log4j.warn").withoutTags()

      override def close: Unit = {}
      override def requiresLayout = false

      override def append(event: LoggingEvent) = event.getLevel match {
        case Level.FATAL => fatal.increment()
        case Level.ERROR => error.increment()
        case Level.WARN => warn.increment()
        case _ =>
      }
    })
  }
}

