package com.tradeshift.reaktive.marshal.scaladsl

import com.tradeshift.reaktive.marshal.{StringMarshallable => S}
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object StringMarshallable {
  val STRING = S.STRING
  val LONG = StringMarshallable[Long]("long",
    s => Try { s.toLong }.recover(prefixMessage("Expecting a signed 64-bit decimal integer ")),
    v => v.toString
  )
  val INT = StringMarshallable[Int]("integer",
    s => Try { s.toInt }.recover(prefixMessage("Expecting a signed 32-bit decimal integer ")),
    v => v.toString
  )
  val BIG_DECIMAL = StringMarshallable[BigDecimal]("big decimal",
    s => Try { BigDecimal(s) }.recover(prefixMessage("Expecting an arbitrary precision decimal number ")),
    v => v.toString
  )
  val BIG_INT = StringMarshallable[BigInt]("big integer",
    s => Try { BigInt(s) }.recover(prefixMessage("Expecting an arbitrary precision decimal integer ")),
    v => v.toString
  )
  val UUID_T = S.UUID_T
  val INSTANT = S.INSTANT
  
  def apply[T](name: String, r: String => Try[T], w: T => String) = new S[T] {
    override def tryRead(value: String) = r(value) match {
      case Success(s) => io.vavr.control.Try.success(s)
      case Failure(x) => io.vavr.control.Try.failure(x)
    }
    
    override def write(t: T) = w(t)
    
    override def toString = name
  }
    
  private def prefixMessage[T](msg: String): PartialFunction[Throwable,T] = {
    case x:IllegalArgumentException => throw new IllegalArgumentException(msg + x.getMessage(), x)
    case other => throw other
  }
}