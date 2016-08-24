package com.tradeshift.reaktive.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.TStringProtocol;

import javaslang.Tuple2;

public class QNameStringProtocol extends TStringProtocol<XMLEvent,QName> {

    public QNameStringProtocol(Protocol<XMLEvent,Tuple2<QName, String>> delegate) {
        super(delegate, XMLProtocol.locator);
    }
    
    /**
     * Returns the protocol only reading and writing local names for _1, without namespace.
     */
    public TStringProtocol<XMLEvent,String> asLocalName() {
        return new TStringProtocol<>(this.map(t -> t.map1(QName::getLocalPart), t -> t.map1(QName::new)), XMLProtocol.locator);
    }
}
