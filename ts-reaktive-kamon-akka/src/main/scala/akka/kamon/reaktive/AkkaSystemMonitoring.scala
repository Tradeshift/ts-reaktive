package akka.kamon.reaktive

import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut

import akka.actor.Actor
import akka.actor.ActorSystemImpl
import akka.actor.DeadLetter
import akka.actor.Props
import kamon.Kamon

@Aspect
class AkkaSystemMonitoring {
  @Pointcut("execution(* akka.actor.ActorSystemImpl.start(..)) && this(system)")
  def actorSystemInitialization(system: ActorSystemImpl): Unit = {}

  @After("actorSystemInitialization(system)")
  def afterActorSystemInitialization(system: ActorSystemImpl): Unit = {
    system.eventStream.subscribe(system.actorOf(Props[AkkaSystemMonitoring.SystemLogger], "dead_letter_counter"), classOf[DeadLetter])      
  }
}

object AkkaSystemMonitoring {
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)
  
  private class SystemLogger extends Actor {
    val metrics = Kamon.metrics.entity(SystemMetrics, context.system.name)

    log.info("Actor system {}'s dead letter count is now monitored into Kamon.", context.system.name)
    
    override def receive = {
      case _:DeadLetter => metrics.deadLetters.increment()
    }
  }
  
  private case object Tick  
}