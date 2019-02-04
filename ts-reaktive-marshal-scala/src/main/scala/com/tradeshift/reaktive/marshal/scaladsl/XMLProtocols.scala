package com.tradeshift.reaktive.marshal.scaladsl

import com.tradeshift.reaktive.marshal.Protocol 
import com.tradeshift.reaktive.xml.XMLProtocol
import javax.xml.namespace.QName
import javax.xml.stream.events.XMLEvent
import com.tradeshift.reaktive.marshal.ReadProtocol
import com.tradeshift.reaktive.xml.TagProtocol
import com.tradeshift.reaktive.xml.TagReadProtocol
import com.tradeshift.reaktive.xml.TagWriteProtocol
import com.tradeshift.reaktive.marshal.WriteProtocol
import javax.xml.stream.events.Namespace
import com.tradeshift.reaktive.xml.AttributeProtocol
import com.tradeshift.reaktive.xml.BodyProtocol
import javax.xml.stream.events.Characters
import com.tradeshift.reaktive.xml.AnyAttributeProtocol

object XMLProtocols {  
  //---------------------- 0-arity tag methods -----------------------------------
  /**
   * Matches an exact XML tag, and emits the start and end tags themselves, including any sub-events that make up its body.
   */
  def tag(name: QName): Protocol[XMLEvent, XMLEvent] =
    XMLProtocol.tag(name)
    
  /**
   * Accepts any single tag when reading, routing its body through to the given inner protocol.
   */
  def anyTag[T](inner: ReadProtocol[XMLEvent, T]) =
    XMLProtocol.anyTag(inner)
    
  //---------------------- 1-arity tag methods -----------------------------------

  /**
   * Reads and writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
   */
  def tag[T](name: QName, p1: Protocol[XMLEvent, T]): TagProtocol[T] =
    XMLProtocol.tag(name, p1)
    
  /**
   * Reads a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
   */
  def tag[T](name: QName, p1: ReadProtocol[XMLEvent, T]): TagReadProtocol[T] =
    XMLProtocol.tag(name, p1)
    
  /**
   * Writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
   */
  def tag[T](name: QName, p1: WriteProtocol[XMLEvent, T]): TagWriteProtocol[T] =
    XMLProtocol.tag(name, p1)
  
  /**
   * Reads and writes a tag and one child element (tag or attribute) using [p1], using [f] to create the result on reading, getting values using [g1] for writing.
   */
  def tag[T,F1](name: QName, p1: Protocol[XMLEvent, F1], f: F1 => T, g1: T => F1): TagProtocol[T] =
    XMLProtocol.tag(name, p1, f1(f), f1(g1))
    
  /**
   * Reads a tag and one child element (tag or attribute) using [p1], creating its result using [f].
   */
  def tag[T,F1](name: QName, p1: ReadProtocol[XMLEvent, F1], f: F1 => T): TagReadProtocol[T] =
    XMLProtocol.tag(name, p1, f1(f))
    
  /**
   * Writes a tag and one child element (tag or attribute) using [p1], getting values using [g1] for writing.
   */
  def tag[T,F1](name: QName, g1: T => F1, p1: WriteProtocol[XMLEvent, F1]): TagWriteProtocol[T] =
    XMLProtocol.tag(name, f1(g1), p1)
    
  //---------------------- 2-arity tag methods -----------------------------------

  /**
   * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
   */
  def tag[T,F1,F2](name: QName, p1: Protocol[XMLEvent,F1], p2: Protocol[XMLEvent,F2], f: (F1,F2)=>T, g1: T=>F1, g2: T=>F2): TagProtocol[T] =
    XMLProtocol.tag(name, p1, p2, f2(f), f1(g1), f1(g2))
    
  /**
   * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
   */
  def tag[T,F1,F2](name: QName, p1: ReadProtocol[XMLEvent,F1], p2: ReadProtocol[XMLEvent,F2], f: (F1,F2)=>T): TagReadProtocol[T] =
    XMLProtocol.tag(name, p1, p2, f2(f))

  /**
   * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
   */
  def tag[T,F1,F2](name: QName, g1: T=>F1, p1: WriteProtocol[XMLEvent,F1], g2: T=>F2, p2: WriteProtocol[XMLEvent,F2]): TagWriteProtocol[T] =
    XMLProtocol.tag(name, f1(g1), p1, f1(g2), p2)
    
  /**
   * Reads and writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
   */
  def tag[T,F1,F2](name: QName, p1: Protocol[XMLEvent,F1], p2: Protocol[XMLEvent,F2]): TagProtocol[(F1,F2)] =
    tag(name, p1, p2, (f1:F1, f2:F2) => (f1,f2), _._1, _._2)  
    
  /**
   * Reads a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
   */
  def tag[T,F1,F2](name: QName, p1: ReadProtocol[XMLEvent,F1], p2: ReadProtocol[XMLEvent,F2]): TagReadProtocol[(F1,F2)] =
    tag(name, p1, p2, (f1:F1, f2:F2) => (f1,f2))
    
  /**
   * Writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
   */
  def tag[T,F1,F2](name: QName, p1: WriteProtocol[XMLEvent,F1], p2: WriteProtocol[XMLEvent,F2]): TagWriteProtocol[(F1,F2)] =
    tag(name, _._1, p1, _._2, p2)
    
  //---------------------- 3-arity tag methods -----------------------------------

  /**
   * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
   */
  def tag[T,F1,F2,F3](name: QName, p1: Protocol[XMLEvent,F1], p2: Protocol[XMLEvent,F2], p3: Protocol[XMLEvent,F3], f: (F1,F2,F3)=>T, g1: T=>F1, g2: T=>F2, g3: T=>F3): TagProtocol[T] =
    XMLProtocol.tag(name, p1, p2, p3, f3(f), f1(g1), f1(g2), f1(g3))
    
  /**
   * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
   */
  def tag[T,F1,F2,F3](name: QName, p1: ReadProtocol[XMLEvent,F1], p2: ReadProtocol[XMLEvent,F2], p3: ReadProtocol[XMLEvent,F3], f: (F1,F2,F3)=>T): TagReadProtocol[T] =
    XMLProtocol.tag(name, p1, p2, p3, f3(f))

  /**
   * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
   */
  def tag[T,F1,F2,F3](name: QName, g1: T=>F1, p1: WriteProtocol[XMLEvent,F1], g2: T=>F2, p2: WriteProtocol[XMLEvent,F2], g3: T=>F3, p3: WriteProtocol[XMLEvent,F3]): TagWriteProtocol[T] =
    XMLProtocol.tag(name, f1(g1), p1, f1(g2), p2, f1(g3), p3)
    
  //---------------------- 4-arity tag methods -----------------------------------

  /**
   * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
   */
  def tag[T,F1,F2,F3,F4](name: QName, p1: Protocol[XMLEvent,F1], p2: Protocol[XMLEvent,F2], p3: Protocol[XMLEvent,F3], p4: Protocol[XMLEvent,F4], f: (F1,F2,F3,F4)=>T, g1: T=>F1, g2: T=>F2, g3: T=>F3, g4: T => F4): TagProtocol[T] =
    XMLProtocol.tag(name, p1, p2, p3, p4, f4(f), f1(g1), f1(g2), f1(g3), f1(g4))
    
  /**
   * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
   */
  def tag[T,F1,F2,F3,F4](name: QName, p1: ReadProtocol[XMLEvent,F1], p2: ReadProtocol[XMLEvent,F2], p3: ReadProtocol[XMLEvent,F3], p4: ReadProtocol[XMLEvent,F4], f: (F1,F2,F3,F4)=>T): TagReadProtocol[T] =
    XMLProtocol.tag(name, p1, p2, p3, p4, f4(f))

  /**
   * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
   */
  def tag[T,F1,F2,F3,F4](name: QName, g1: T=>F1, p1: WriteProtocol[XMLEvent,F1], g2: T=>F2, p2: WriteProtocol[XMLEvent,F2], g3: T=>F3, p3: WriteProtocol[XMLEvent,F3], g4: T=>F4, p4: WriteProtocol[XMLEvent,F4]): TagWriteProtocol[T] =
    XMLProtocol.tag(name, f1(g1), p1, f1(g2), p2, f1(g3), p3, f1(g4), p4)
    
  //---------------------- 1-arity tagName methods -----------------------------------

  /**
   * Reads and writes a tag with any name
   */
  def tagName: Protocol[XMLEvent, QName] = 
    XMLProtocol.tagName
    
  /**
   * Reads and writes a tag with any name, using [f] to create the result on reading, getting values using [g1] for writing.
   */
  def tagName[T](f: QName => T, g: T => QName): TagProtocol[T] =
    XMLProtocol.tagName(f1(f), f1(g))
    
  /**
   * Reads a tag with any name, creating its result using [f].
   */
  def readTagName[T](f: QName => T): TagReadProtocol[T] =
    XMLProtocol.readTagName(f1(f))
    
  /**
   * Writes a tag and with any name, getting values using [g1] for writing.
   */
  def writeTagName[T](g1: T => QName): TagWriteProtocol[T] =
    XMLProtocol.writeTagName(f1(g1))

  //---------------------- 2-arity tagName methods -----------------------------------

  /**
   * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
   */
  def tagName[F2,T](p2: Protocol[XMLEvent,F2], f: (QName,F2)=>T, g1: T=>QName, g2: T=>F2): TagProtocol[T] =
    XMLProtocol.tagName(p2, f2(f), f1(g1), f1(g2))
    
  /**
   * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
   */
  def tagName[F2,T](p2: ReadProtocol[XMLEvent,F2], f: (QName,F2)=>T): TagReadProtocol[T] =
    XMLProtocol.tagName(p2, f2(f))
    
  /**
   * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
   */
  def tagName[F2,T](g1: T=>QName, g2: T=>F2, p2: WriteProtocol[XMLEvent,F2]): TagWriteProtocol[T] =
    XMLProtocol.tagName(f1(g1), f1(g2), p2)
    
  /**
   * Reads and writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
   */
  def tagNameAnd[F2](p2: Protocol[XMLEvent,F2]): TagProtocol[(QName,F2)] =
    tagName(p2, (n:QName,f:F2) => (n,f), _._1, _._2)
   
  /**
   * Reads a tag with any name and inner protocols using [p*], represented by a Tuple2.
   */
  def tagNameAnd[F2](p2: ReadProtocol[XMLEvent,F2]): TagReadProtocol[(QName,F2)] =
    tagName(p2, (n:QName,f:F2) => (n,f))
 
  /**
   * Writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
   */
  def tagNameAnd[F2](p2: WriteProtocol[XMLEvent,F2]): TagWriteProtocol[(QName,F2)] =
    tagName(_._1, _._2, p2)

  //---------------------- 3-arity tagName methods -----------------------------------

  /**
   * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
   */
  def tagName[T,F2,F3](p2: Protocol[XMLEvent,F2], p3: Protocol[XMLEvent,F3], f: (QName,F2,F3)=>T, g1: T=>QName, g2: T=>F2, g3: T=>F3): TagProtocol[T] =
    XMLProtocol.tagName(p2, p3, f3(f), f1(g1), f1(g2), f1(g3))
    
  /**
   * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
   */
  def tagName[T,F2,F3](p2: ReadProtocol[XMLEvent,F2], p3: ReadProtocol[XMLEvent,F3], f: (QName,F2,F3)=>T): TagReadProtocol[T] =
    XMLProtocol.tagName(p2, p3, f3(f))

  /**
   * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
   */
  def tagName[T,F2,F3](g1: T=>QName, g2: T=>F2, p2: WriteProtocol[XMLEvent,F2], g3: T=>F3, p3: WriteProtocol[XMLEvent,F3]): TagWriteProtocol[T] =
    XMLProtocol.tagName(f1(g1), f1(g2), p2, f1(g3), p3)
    
  // --------------------------------------------------------------------------------

  /**
   * Reads and writes a namespaced string attribute
   */
  def attribute(ns: Namespace, name: String): AttributeProtocol =
    XMLProtocol.attribute(ns, name)
    
  /**
   * Reads and writes a string attribute in the default namespace
   */
  def attribute(name: String): AttributeProtocol =
    XMLProtocol.attribute(name)
    
  /**
   * Reads and writes a namespaced string attribute
   */
  def attribute(name: QName): AttributeProtocol =
    XMLProtocol.attribute(name)
    
  /**
   * Reads and writes the body of the current XML tag
   */
  def body: BodyProtocol =
    XMLProtocol.body
    
  /**
   * Reads and writes the body of the current XML tag as actual XML {@link Characters} events.
   */
  def bodyEvents: Protocol[XMLEvent,Characters] =
    XMLProtocol.bodyEvents
    
  /**
   * Returns a QName for a tag in the default namespace.
   */
  def qname(name: String) =
    XMLProtocol.qname(name)
    
  /**
   * Combines a Namespace and local name into a QName.
   */
  def qname(ns: Namespace, name: String) =
    XMLProtocol.qname(ns, name)
  
  /**
   * Creates a Namespace that can be used both for reading and writing XMl.
   */
  def ns(prefix: String, namespace: String) =
    XMLProtocol.ns(prefix, namespace)
    
  /**
   * Creates a Namespace that can only be used for reading XML.
   */
  def ns(namespace: String) =
    XMLProtocol.ns(namespace)
    
  /**
   * Reads and writes every top-level tag's QName and its body as a Tuple2.
   */
  def anyTagWithBody: QNameStringProtocol = 
    new QNameStringProtocol(tagNameAnd(body))
  
  /**
   * Reads and writes every top-level tag's QName and the (required) attribute [name] as a Tuple2.
   */
  def anyTagWithAttribute(name: String): QNameStringProtocol =
    new QNameStringProtocol(tagNameAnd(attribute(name)))
  
  /**
   * Reads and writes every top-level attribute's QName and its value as a Tuple2.
   */
  def anyAttribute: QNameStringProtocol =
    new QNameStringProtocol(AnyAttributeProtocol.INSTANCE.map(
      f1(t => (t._1, t._2)),
      f1(t => io.vavr.Tuple.of(t._1, t._2))
    ))
}
