package akka.http.impl.engine.client

import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut

import akka.http.impl.engine.client.PoolInterfaceActor.PoolRequest
import akka.http.impl.settings.HostConnectionPoolSetup
import akka.stream.impl.Buffer
import kamon.Kamon

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
    Kamon.metrics.gauge("http-client.pool.queue.used", tags) { () => buffer.used.toLong }
    Kamon.metrics.gauge("http-client.pool.queue.capacity", tags) { () => buffer.capacity.toLong }
  }
  
  @Pointcut("execution(* akka.http.impl.engine.client.PoolInterfaceActor.postStop(..)) && this(actor)")
  def stop(actor: PoolInterfaceActor): Unit = {}
  
  @After("stop(actor)")
  def afterStop(actor: PoolInterfaceActor): Unit = {
    hcpsField.setAccessible(true)
    val hcps = hcpsField.get(actor).asInstanceOf[HostConnectionPoolSetup]

    val tags = Map("target_host" -> hcps.host, "target_port" -> hcps.port.toString)
    Kamon.metrics.removeGauge("http-client.pool.queue.used", tags)
    Kamon.metrics.removeGauge("http-client.pool.queue.capacity", tags)
  }
}