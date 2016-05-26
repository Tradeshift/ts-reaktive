package com.tradeshift.reaktive.kamon.akka

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

object AkkaSystemMonitoring extends ExtensionId[AkkaSystemMonitoringExtension] with ExtensionIdProvider  {
  override def createExtension(system: ExtendedActorSystem) = new AkkaSystemMonitoringExtensionImpl(system)
  override def lookup(): ExtensionId[_ <: Extension] = AkkaSystemMonitoring  
}

trait AkkaSystemMonitoringExtension extends Kamon.Extension {
  
}

class AkkaSystemMonitoringExtensionImpl(system: ExtendedActorSystem) extends AkkaSystemMonitoringExtension {
  system.eventStream.subscribe(system.actorOf(Props[SystemLogger], "dead_letter_counter"), classOf[DeadLetter])
  
  private class SystemLogger extends Actor {
    val metrics = Kamon.metrics.entity(SystemMetrics, system.name)
    
    override def receive = {
      case _:DeadLetter => metrics.deadLetters.increment()
    }
  }
  
  private case object Tick
}
