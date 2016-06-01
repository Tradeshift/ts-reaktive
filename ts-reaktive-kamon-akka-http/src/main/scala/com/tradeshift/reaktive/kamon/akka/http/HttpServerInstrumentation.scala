package com.tradeshift.reaktive.kamon.akka.http

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around

import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory

import akka.NotUsed
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Flow

@Aspect
class HttpServerInstrumentation {
  private val log = LoggerFactory.getLogger(getClass);
  
  @Pointcut("execution(* akka.http.scaladsl.HttpExt.bindAndHandle(..))")
  def bindAndHandle: Unit = {}
  
  @Around("bindAndHandle()")
  def wrap(jp: ProceedingJoinPoint) = {
    try {      
      val port = jp.getArgs()(2).asInstanceOf[Int]
      val connectionContext = jp.getArgs()(3).asInstanceOf[ConnectionContext]
      val effectivePort = if (port >= 0) port else connectionContext.defaultPort
      val name = s"${effectivePort}"
      val flow = jp.getArgs()(0)
      val wrapped = RequestLogger.apply(flow.asInstanceOf[Flow[HttpRequest, HttpResponse, NotUsed]], name)
      val args: Array[Object] = jp.getArgs
      args(0) = wrapped
      log.info("Logging http server {} metrics into Kamon.", name)
      jp.proceed(args)
    } catch {
      case x:Throwable =>
        log.error("Could not enable akka-http server monitoring", x)
        jp.proceed()
    }
  }

}