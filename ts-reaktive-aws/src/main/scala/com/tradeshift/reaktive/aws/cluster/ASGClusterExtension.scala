package com.tradeshift.reaktive.aws.cluster

import akka.actor.{ActorSystem, AddressFromURIString, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.cluster.Cluster
import com.tradeshift.reaktive.aws.AWSClient
import org.slf4j.{Logger, LoggerFactory}

/**
  * Extension for cluster forming based on seed nodes from AWS Auto Scaling Group.
  * {{{akka.cluster.seed-nodes}}} property should be empty when extension is applied.
  * Because it hides {{{Cluster.joinSeedNodes}}} that is being used here.
  */
object ASGClusterExtension extends ExtensionId[ASGClusterExtension] with ExtensionIdProvider {

    /**
      * The lookup method is required by ExtensionIdProvider, we return ourselves here.
      * This allows us to configure our extension to be loaded when the ActorSystem starts up.
      */
    override def lookup = ASGClusterExtension

    /**
      * This method will be called by Akka to instantiate Extension
      */
    override def createExtension(system: ExtendedActorSystem) = new ASGClusterExtension(system) // NOTEST

    /**
      * Java API: Retrieve the Extension for the given system.
      */
    override def get(system: ActorSystem): ASGClusterExtension = super.get(system)
}

/**
  * Extension requests list of nodes from AWS Auto Scaling Group.
  * It should be registered on all nodes that want to join cluster.
  * Only one node will receive all list of nodes, other nodes will get sibling nodes (excluding themselves).
  */
class ASGClusterExtension private(system: ExtendedActorSystem) extends Extension {
    val log: Logger = LoggerFactory.getLogger(getClass.getName)
    val port: Int = system.settings.config.getInt("akka.remote.netty.tcp.port")

    private val nodes = AWSClient().candidateSeedNodes                                                   // NOTEST no AWS integration in tests
      .map(inst => AddressFromURIString(s"akka.tcp://${system.name}@${inst.getPrivateIpAddress}:$port")) // NOTEST no AWS integration in tests

    log.debug("Joining cluster nodes {}", nodes)        // NOTEST no AWS integration in tests
    Cluster(system).joinSeedNodes(nodes)                // NOTEST no AWS integration in tests
}