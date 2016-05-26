package com.tradeshift.reaktive.kamon.akka.http

import org.slf4j.LoggerFactory

import akka.NotUsed
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Attributes
import akka.stream.BidiShape
import akka.stream.Inlet
import akka.stream.Outlet

import akka.stream.scaladsl.BidiFlow
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import kamon.Kamon

object RequestLogger {
  private val log = LoggerFactory.getLogger(getClass);

  def apply(flow: Flow[HttpRequest, HttpResponse, NotUsed], entityName: String): Flow[HttpRequest, HttpResponse, NotUsed] = 
    BidiFlow.fromGraph(wrapper(entityName)).join(flow)
  
  private def wrapper(entityName: String) = new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
    val requestIn = Inlet[HttpRequest]("request.in")
    val requestOut = Outlet[HttpRequest]("request.out")
    val responseIn = Inlet[HttpResponse]("response.in")
    val responseOut = Outlet[HttpResponse]("response.out")
    
    val metrics = Kamon.metrics.entity(HttpMetrics, entityName);
    
    override val shape = BidiShape(requestIn, requestOut, responseIn, responseOut)
    
    override def createLogic(attr:Attributes) = new GraphStageLogic(shape) {
      val requests = collection.mutable.Queue.empty[Long]
      
      setHandler(requestIn, new InHandler {
        override def onPush() = {
          requests.enqueue(System.nanoTime())
          val request = grab(requestIn)
          metrics.recordRequest()
          push(requestOut, request)          
        }
      })

      setHandler(requestOut, new OutHandler {
        override def onPull() = pull(requestIn)
      })
      
      setHandler(responseIn, new InHandler {
        override def onPush() = {
          val response = grab(responseIn);
          if (requests.isEmpty) {
              log.warn("Huh, got a response without having seen a request: {}", response);
          } else {
              val started = requests.dequeue()
              metrics.recordResponse(System.nanoTime() - started, response);
          }
          push(responseOut, response);
        }
      })

      setHandler(responseOut, new OutHandler {
        override def onPull() = pull(responseIn)
      })
      
      override def preStart() = metrics.recordConnectionOpened()
      
      override def postStop() = metrics.recordConnectionClosed()
    }
  }
}