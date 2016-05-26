package com.tradeshift.reaktive.kamon.log4j

import kamon.metric.instrument.InstrumentFactory
import kamon.metric.GenericEntityRecorder
import kamon.metric.EntityRecorderFactory

class Log4jMetrics(factory: InstrumentFactory) extends GenericEntityRecorder(factory) {  
  val error = counter("error")
  val warn = counter("warn")
  val fatal = counter("fatal")
}

object Log4jMetrics extends EntityRecorderFactory[Log4jMetrics] {
  def category: String = "log4j"
  def createRecorder(instrumentFactory: InstrumentFactory): Log4jMetrics = new Log4jMetrics(instrumentFactory)
}
