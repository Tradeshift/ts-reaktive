package com.tradeshift.reaktive.marshal.scaladsl

import com.tradeshift.reaktive.marshal.Protocol
import javax.xml.namespace.QName
import javax.xml.stream.events.XMLEvent
import com.tradeshift.reaktive.xml.XMLProtocol

/**
 * Protocol for a tuple of QName and String, where the String can be transformed to other types
 * by invoking .as(TYPE).
 */
class QNameStringProtocol(delegate: Protocol[XMLEvent,(QName, String)]) 
extends TStringProtocol[XMLEvent,QName](delegate, XMLProtocol.locator) {
  /**
   * Returns the protocol only reading and writing local names for _1, without namespace.
   */
  def asLocalName: TStringProtocol[XMLEvent,String] = new TStringProtocol(
    this.map(
      f1(t => t.copy(_1 = t._1.getLocalPart)),
      f1(t => t.copy(_1 = new QName(t._1)))
    ), XMLProtocol.locator)  
}