package akka.kamon.cluster

import akka.NotUsed

import scala.concurrent.duration._
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import akka.actor.Actor
import akka.actor.ActorSystemImpl
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterActorRefProvider
import kamon.Kamon
import kamon.tag.TagSet

@Aspect
class AkkaClusterMonitoring {
  import AkkaClusterMonitoring._

  @Pointcut("execution(* akka.actor.ActorSystemImpl.start(..)) && this(system)")
  def actorSystemInitialization(system: ActorSystemImpl): Unit = {}

  @After("actorSystemInitialization(system)")
  def afterActorSystemInitialization(system: ActorSystemImpl): Unit = {
    system.provider match {
      case c: ClusterActorRefProvider ⇒
        system.actorOf(Props[ClusterLogger], "clusterLogger")
      case _ =>
        log.info("Not starting cluster logging, since actor system {} doesn't have a ClusterActorRefProvider.", system.name)
    }
  }
}

object AkkaClusterMonitoring {
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)
  private val _members = Kamon.histogram("akka-cluster.members").withoutTags()
  private val _seenBy = Kamon.histogram("akka-cluster.seenBy").withoutTags()
  private val _unreachable = Kamon.histogram("akka-cluster.unreachable").withoutTags()
  private val _isLeader = Kamon.histogram("akka-cluster.isLeader").withoutTags()

  private class ClusterLogger extends Actor {
    val cluster = Cluster.get(context.system)
    val tags = TagSet.builder().add("akka-cluster", context.system.name).build()
    val members = _members.withTags(tags)
    val seenby = _seenBy.withTags(tags)
    val unreachable = _unreachable.withTags(tags)
    val isleader = _isLeader.withTags(tags)

    import context.dispatcher
    val interval = 1.second
    val job = context.system.scheduler.schedule(interval, interval, self, NotUsed);

    log.info("actor system {}'s cluster metrics are now monitored into kamon.", context.system.name)

    override def postStop() {
      job.cancel()
    }

    override def receive = {
      case tick =>
        val state = cluster.state
        members.record(state.members.size);
        seenby.record(state.seenBy.size);
        unreachable.record(state.unreachable.size);
        isleader.record(if (state.leader == Some(cluster.selfAddress)) 1 else 0);
    }
  }

  private case object Tick
}
