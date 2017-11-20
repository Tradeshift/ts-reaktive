package com.tradeshift.reaktive.aws.cluster

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest._

class ASGClusterExtensionSpec extends WordSpec {

  "ASGClusterExtension" should {
    "fail with Exception because no integration with AWS in tests" in {
      assertThrows[Exception] {
        ActorSystem("ASGClusterExtensionSpec",
          ConfigFactory.parseString(
            s"""
               |akka.remote.netty.tcp.port = 2291
               |akka.extensions = ["${classOf[ASGClusterExtension].getName}"]
               |akka.cluster.seed-nodes = []
             """.stripMargin))
      }
    }
  }
}
