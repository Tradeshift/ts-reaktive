package com.tradeshift.reaktive.marshal.scaladsl

import com.tradeshift.reaktive.marshal.Reader
import javax.xml.stream.events.XMLEvent
import java.util.stream.Collectors
import scala.collection.JavaConverters._

/**
 * Interface to and from stax to the XML marshalling framework.
 */
object Stax {
  private val delegate = new com.tradeshift.reaktive.xml.Stax
  
  def parse[T](xml: String, reader: Reader[XMLEvent,T]): Seq[T] =
    delegate.parse(xml, reader).collect(Collectors.toList[T]).asScala
}