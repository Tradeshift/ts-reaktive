package com.tradeshift.reaktive.kamon.akka

import kamon.metric.instrument.InstrumentFactory
import kamon.metric.GenericEntityRecorder
import kamon.metric.EntityRecorderFactory

class SystemMetrics(factory: InstrumentFactory) extends GenericEntityRecorder(factory) {  
  val deadLetters = counter("deadLetters")
}

object SystemMetrics extends EntityRecorderFactory[SystemMetrics] {
  def category: String = "akka-system"
  def createRecorder(instrumentFactory: InstrumentFactory): SystemMetrics = new SystemMetrics(instrumentFactory)
}
