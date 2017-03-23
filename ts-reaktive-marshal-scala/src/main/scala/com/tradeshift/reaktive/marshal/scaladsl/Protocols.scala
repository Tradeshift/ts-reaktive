package com.tradeshift.reaktive.marshal.scaladsl

import scala.collection.immutable
import com.tradeshift.reaktive.marshal.Protocol 
import scala.reflect.ClassTag
import com.tradeshift.reaktive.marshal.ReadProtocol
import com.tradeshift.reaktive.marshal.WriteProtocol
import java.util.function.Supplier
import java.util.function.BiFunction
import java.util.function.Consumer

object Protocols {

  // ----------------------- Alternatives -----------------------------
  
  /**
   * Returns a protocol that considers all events emitted results, and vice-versa.
   */
  def identity[T: ClassTag]: Protocol[T,T] = 
    Protocol.identity(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  /**
   * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple
   * alternatives emit for the same event, the first one wins.
   */
  def anyOf[E,T](first: ReadProtocol[E,T], second: ReadProtocol[E,T], others: ReadProtocol[E,T]*): ReadProtocol[E,T] =
    Protocol.anyOf(first, second, others: _*)
    
  /**
   * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple
   * alternatives emit for the same event, the first one wins.
   * 
   * Always picks the first alternative during writing.
   */
  def anyOf[E,T](first: Protocol[E,T], second: Protocol[E,T], others: Protocol[E,T]*): Protocol[E,T] =
    Protocol.anyOf(first, second, others: _*)

  /**
   * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit.
   * If multiple alternatives emit for the same event, all results are emitted.
   * If at least one alternative emits for an event, any errors on other alternatives are ignored.
   * If all alternatives yield errors for an event, the errors are concatenated and escalated.
   */
  def combine[E,T](first: ReadProtocol[E,T], second: ReadProtocol[E,T], others: ReadProtocol[E,T]*): ReadProtocol[E,immutable.Seq[T]] =
    Protocol.combine(first, second, others: _*)
  
  // ----------------------- Collections -----------------------------

  /**
   * Reads an inner protocol multiple times. On reading, creates a {@link Vector} to represent it.
   */
  def vector[E,T](inner: ReadProtocol[E,T]): ReadProtocol[E,Vector[T]] =
    Protocol.vector(inner)
        
  /**
   * Reads and writes an inner protocol multiple times. On reading, creates a {@link Vector} to hold the values.
   * On writing, any {@link Seq} will do.
   */
  def vector[E,T](inner: Protocol[E,T]): Protocol[E,Seq[T]] =
    Protocol.vector(inner)
    
  /**
   * Writes an inner protocol multiple times, represented by a {@link Seq}.
   */
  def seq[E,T](inner: WriteProtocol[E,T]): WriteProtocol[E,Seq[T]] =
    Protocol.seq(inner)
    
  /**
   * Writes an inner protocol multiple times, represented by a {@link Iterable}.
   */
  def iterable[E,T](inner: WriteProtocol[E,T]): WriteProtocol[E,Iterable[T]] =
    Protocol.iterable(inner)
    
  /**
   * Folds over a repeated nested protocol, merging the results into a single element.
   */
  def foldLeft[E,T,U](inner: ReadProtocol[E,T], initial: () => U, combine: (U, T) => U): ReadProtocol[E,U] =
    Protocol.foldLeft(inner, 
      new Supplier[U] { override def get = initial() },
      f2(combine))
    
  /**
   * Invokes the given function for every item the inner protocol emits, while emitting a single Unit as outer value.
   */
  def forEach[E,T](inner: ReadProtocol[E,T], consume: T => Unit): ReadProtocol[E,Unit] =
    Protocol.forEach(inner, new Consumer[T] { override def accept(t:T) = consume(t) })
            .map(f1(void => ()))
            
  /**
   * Reads and writes an inner protocol of tuples multiple times. On reading, creates a {@link io.vavr.collection.HashMap} to hold the result.
   * On writing, any {@link io.vavr.collection.Map} will do.
   */
  def hashMap[E,K,V](inner: Protocol[E,(K,V)]): Protocol[E,scala.collection.immutable.Map[K,V]] =
    Protocol.hashMap(inner.map(
      f1(t => io.vavr.Tuple.of(t._1, t._2)),
      f1((t:io.vavr.Tuple2[K,V]) => (t._1, t._2))
    ))
      
  /**
   * Reads an inner protocol of tuples multiple times. On reading, creates a {@link HashMap} to hold the result.
   */
  def hashMap[E,K,V](inner: ReadProtocol[E,(K,V)]): ReadProtocol[E,scala.collection.immutable.HashMap[K,V]] =
    Protocol.hashMap(inner.map(
      f1(t => io.vavr.Tuple.of(t._1, t._2))
    ))
    
  /**
   * Writes a map using an inner protocol, by turning it into writing multiple tuples.
   */
  def map[E,K,V](inner: WriteProtocol[E,(K,V)]): WriteProtocol[E,Map[K,V]] =
    Protocol.map(inner.compose(f1((t: io.vavr.Tuple2[K,V]) => (t._1, t._2))))
    
  /**
   * Reads and writes a nested protocol optionally, representing it by a {@link Option}.
   */
  def option[E,T](inner: Protocol[E,T]): Protocol[E,Option[T]] =
    Protocol.option(inner)
    
  /**
   * Reads a nested protocol optionally, representing it by a {@link Option}.
   */
  def option[E,T](inner: ReadProtocol[E,T]): ReadProtocol[E,Option[T]] =
    Protocol.option(inner)
    
  /**
   * Writes a nested protocol optionally, representing it by a {@link io.vavr.control.Option}.
   */
  def option[E,T](inner: WriteProtocol[E,T]): WriteProtocol[E,Option[T]] =
    Protocol.option(inner)
    
  /**
   * Reads a nested protocol optionally, supplying [value] if it does not yield a result.
   */
  def withDefault[E,T](value: T, inner: Protocol[E,T]): Protocol[E,T] = 
    Protocol.withDefault(value, inner)
    
  /**
   * Reads a nested protocol optionally, supplying [value] if it does not yield a result.
   */
  def withDefault[E,T](value: T, inner: ReadProtocol[E,T]): ReadProtocol[E,T] = 
    Protocol.withDefault(value, inner)
    
  object Implicits {
    implicit def widen[E, T, U >: T](p: ReadProtocol[E,T]): ReadProtocol[E,U] = p.asInstanceOf[ReadProtocol[E,U]]
  }
}