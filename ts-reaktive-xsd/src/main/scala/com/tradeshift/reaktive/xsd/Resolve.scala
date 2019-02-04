package com.tradeshift.reaktive.xsd

import scala.reflect.ClassTag
import scala.collection.mutable

trait Resolve[+V] {
  def get: V
}

case class Unresolved[K,V](key: K) extends Resolve[V] {
  private var value: Option[V] = None
  override def get = value.get
  private[xsd] def resolve(v: V) = {
    if (value.isDefined) {
      throw new IllegalStateException("Resolving twice:" + key)
    }
    value = Some(v)
  }
}

case class Resolved[+V](get: V) extends Resolve[V]

/**
  * A Map where not all values are immediately known. It invokes callbacks as values become known. 
  * Hence, a value cannot be directly looked up; only a callback can be registered, that will then eventually
  * be invoked once a value is known. 
  * 
  * This class is synchronous, and not multi-thread safe.
  */
class ResolveMap[K,V: ClassTag](initial: Map[K,V]) {
  private val known = mutable.Map.empty[K,Resolved[V]] ++ initial.map { case(k,v) => k -> Resolved(v) }
  private var missing = Map.empty[K,Unresolved[K,V]]

  def update(key: K, value: V): Unit = {
    if (known.contains(key)) {
      throw new IllegalArgumentException("Trying to define twice: " + key)
    }
    known(key) = Resolved(value)
    missing.get(key).foreach(_.resolve(value))
    missing -= key
  }

  def apply(key: K): Resolve[V] =
    known.get(key) match {
      case Some(resolved) =>
        resolved
      case None =>
        if (!missing.contains(key)) {
          missing += (key -> Unresolved(key))
        }
        missing(key)
    }

  def isResolved: Boolean = missing.isEmpty

  def missingKeys: Iterable[K] = missing.keys

  def values: Iterable[V] = known.values.collect { case Resolved(v) => v }

  def isKnown(key: K) = known.contains(key)
}
