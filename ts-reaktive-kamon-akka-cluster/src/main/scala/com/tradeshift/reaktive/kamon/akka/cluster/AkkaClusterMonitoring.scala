package com.tradeshift.reaktive.kamon.akka.cluster

import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import kamon.Kamon
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.Actor
import akka.cluster.Cluster
import scala.concurrent.duration._
import scala.util.Try
import akka.actor.Props

object AkkaClusterMonitoring extends ExtensionId[AkkaClusterMonitoringExtension] with ExtensionIdProvider  {
  override def createExtension(system: ExtendedActorSystem) = new AkkaClusterMonitoringExtensionImpl(system)
  override def lookup(): ExtensionId[_ <: Extension] = AkkaClusterMonitoring  
}

trait AkkaClusterMonitoringExtension extends Kamon.Extension {
  
}

class AkkaClusterMonitoringExtensionImpl(system: ExtendedActorSystem) extends AkkaClusterMonitoringExtension {
  system.actorOf(Props[ClusterLogger], "clusterLogger")
  
  private class ClusterLogger extends Actor {
    val cluster = Cluster.get(context.system)
    val metrics = Kamon.metrics.entity(ClusterMetrics, system.name)
    
    import context.dispatcher
    val interval = 1.second
    val job = context.system.scheduler.schedule(interval, interval, self, Tick);
    
    override def postStop() {
      job.cancel()
    }
    
    override def receive = {
      case Tick =>
        val state = cluster.state
        metrics.members.record(state.members.size);
        metrics.seenBy.record(state.seenBy.size);
        metrics.unreachable.record(state.unreachable.size);
        metrics.isLeader.record(if (state.leader == Some(cluster.selfAddress)) 1 else 0);    
    }
  }
  
  private case object Tick
}
