package com.tradeshift.reaktive.marshal.scaladsl

import com.tradeshift.reaktive.marshal.{StringMarshallable => JStringMarshallable}
import com.tradeshift.reaktive.marshal.Protocol
import com.tradeshift.reaktive.marshal.Locator
import com.tradeshift.reaktive.marshal.StringMarshallableProtocol

/**
 * Protocol for a tuple of T and String, where the String can be transformed to other types
 * by invoking .as(TYPE).
 */
class TStringProtocol[E,T1](delegate: Protocol[E, (T1,String)], locator: Locator[E]) extends Protocol[E, (T1,String)] {
  override def getEventType() = delegate.getEventType
  override def reader() = delegate.reader()
  override def writer() = delegate.writer()
  override def empty() = delegate.empty()
  override def toString() = delegate.toString()
  
  /**
   * Converts _2 of the tuple to a different type.
   */
  def as[T2](targetType: JStringMarshallable[T2]): Protocol[E, (T1,T2)] = new Protocol[E, (T1,T2)] {
    override def getEventType = delegate.getEventType
    override def toString() = s"${delegate} as ${targetType}"
    override def writer = delegate.writer().compose(f1(t => t.copy(_2 = targetType.write(t._2))))
    override def reader = StringMarshallableProtocol.addLocationOnError(
      delegate.reader().flatMap(f1(t => 
        targetType.tryRead(t._2).map(f1(t2 => (t._1, t2)))
      )),
      locator)
    override def empty = delegate.empty().flatMap(f1(t =>
        targetType.tryRead(t._2).map(f1(t2 => (t._1, t2)))
      ))
  }
}