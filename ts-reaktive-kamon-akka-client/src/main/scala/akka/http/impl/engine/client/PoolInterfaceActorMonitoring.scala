package akka.http.impl.engine.client

import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut

import scala.concurrent.duration._
import akka.http.impl.engine.client.PoolInterfaceActor.PoolRequest
import akka.http.impl.settings.HostConnectionPoolSetup
import akka.stream.impl.Buffer
import kamon.Kamon
import kamon.metric.SingleInstrumentEntityRecorder

@Aspect
class PoolInterfaceActorMonitoring {
  val inputBufferField = classOf[PoolInterfaceActor].getDeclaredField("akka$http$impl$engine$client$PoolInterfaceActor$$inputBuffer")
  val hcpsField = classOf[PoolInterfaceActor].getDeclaredField("akka$http$impl$engine$client$PoolInterfaceActor$$hcps")
  
  @Pointcut("execution(akka.http.impl.engine.client.PoolInterfaceActor.new(..)) && this(actor)")
  def create(actor: PoolInterfaceActor): Unit = {}
  
  @After("create(actor)")
  def afterCreate(actor: PoolInterfaceActor): Unit = {
    inputBufferField.setAccessible(true)
    val buffer = inputBufferField.get(actor).asInstanceOf[Buffer[PoolRequest]]
    hcpsField.setAccessible(true)
    val hcps = hcpsField.get(actor).asInstanceOf[HostConnectionPoolSetup]
    
    val tags = Map("target_host" -> hcps.host, "target_port" -> hcps.port.toString)
    Kamon.metrics.registerGauge("http-client.pool.queue.used", { () => buffer.used.toLong }, tags = tags, refreshInterval = Some(1.second)) 
    Kamon.metrics.gauge("http-client.pool.queue.capacity", tags) { () => buffer.capacity.toLong }
  }
  
  @Pointcut("execution(* akka.http.impl.engine.client.PoolInterfaceActor.postStop(..)) && this(actor)")
  def stop(actor: PoolInterfaceActor): Unit = {}
  
  @After("stop(actor)")
  def afterStop(actor: PoolInterfaceActor): Unit = {
    hcpsField.setAccessible(true)
    val hcps = hcpsField.get(actor).asInstanceOf[HostConnectionPoolSetup] 
    val tags = Map("target_host" -> hcps.host, "target_port" -> hcps.port.toString)
    
    for (suffix <- Seq("used", "capacity")) {
      val name = s"http-client.pool.queue.${suffix}"
      for (i <- Kamon.metrics.find(name, SingleInstrumentEntityRecorder.Gauge, tags)) {
        Kamon.metrics.removeGauge(name, tags)
        // Workaround for https://github.com/kamon-io/Kamon/issues/476
        i.cleanup
      }      
    }
  }
}