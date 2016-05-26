package com.tradeshift.reaktive.kamon.akka.http

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import kamon.metric.EntityRecorderFactory

class HttpMetrics(factory: InstrumentFactory) extends GenericEntityRecorder(factory) {
  private val statusInformational = counter("response-status-informational")
  private val statusSuccess = counter("response-status-success")
  private val statusRedirection = counter("response-status-redirection")
  private val statusClientError = counter("response-status-clienterror")
  private val statusServerError = counter("response-status-servererror")
  private val requestActive = minMaxCounter("request-active")
  private val requestDuration = histogram("request-duration")
  private val connectionOpen = minMaxCounter("connection-open")
  
  def recordRequest() {
    requestActive.increment()
  }
  
  def recordResponse(duration: Long, response: HttpResponse) {
    requestActive.decrement()
    requestDuration.record(duration)
    response.status match {
      case Informational(_) => statusInformational.increment()
      case Success(_) => statusSuccess.increment()
      case Redirection(_) => statusRedirection.increment()
      case ClientError(_) => statusClientError.increment()
      case ServerError(_) => statusServerError.increment()
      case CustomStatusCode(_) =>
    }
  }
  
  def recordConnectionOpened() = connectionOpen.increment()
  def recordConnectionClosed() = connectionOpen.decrement()
}

object HttpMetrics extends EntityRecorderFactory[HttpMetrics] {
  def category: String = "akka-http"
  def createRecorder(instrumentFactory: InstrumentFactory): HttpMetrics = new HttpMetrics(instrumentFactory)
}
