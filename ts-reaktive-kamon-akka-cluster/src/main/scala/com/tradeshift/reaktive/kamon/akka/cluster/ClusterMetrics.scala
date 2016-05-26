package com.tradeshift.reaktive.kamon.akka.cluster

import kamon.metric.instrument.InstrumentFactory
import kamon.metric.GenericEntityRecorder
import kamon.metric.EntityRecorderFactory
import akka.cluster.ClusterEvent.CurrentClusterState

class ClusterMetrics(factory: InstrumentFactory) extends GenericEntityRecorder(factory) {  
  val members = histogram("members")
  val seenBy = histogram("seenBy")
  val unreachable = histogram("unreachable")
  val isLeader = histogram("isLeader")
}

object ClusterMetrics extends EntityRecorderFactory[ClusterMetrics] {
  def category: String = "akka-cluster"
  def createRecorder(instrumentFactory: InstrumentFactory): ClusterMetrics = new ClusterMetrics(instrumentFactory)
}
