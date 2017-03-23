package com.tradeshift.reaktive.marshal.scaladsl

import org.scalatest.FunSpec
import com.tradeshift.reaktive.marshal.Protocol
import javax.xml.stream.events.XMLEvent
import org.scalatest.Matchers
import Stax.parse

class XMLProtocolsSpec extends FunSpec with Matchers {
  case class DTO1(l: Long, i: Option[Int], s: Seq[String])
  
  describe("A protocol mapping both attributes and child tags") {
    import Protocols._
    import XMLProtocols._
    import StringMarshallable._
    
    val dto1proto: Protocol[XMLEvent,DTO1] = 
        tag(qname("dto"),
            tag(qname("l"),
                body.as(LONG)
            ),
            option(
                attribute("i").as(INT)
            ),
            vector(
                tag(qname("s"),
                    body
                )
            ),
            DTO1,
            _.l,
            _.i,
            _.s
        )
        
    it("should read a document from XML where the attribute and child tags are absent") {
      parse("<dto><l>123</l></dto>", dto1proto.reader) should be(
        Seq(DTO1(123, None, Seq.empty))
      )
    }
    
    it("should read a complete document from XML") {
      parse("<dto i='42'><s>One</s><l>123</l><s>Two</s></dto>", dto1proto.reader) should be(
        Seq(new DTO1(123, Some(42), Seq("One", "Two")))
      )
    }

    it("should fail if an optional attribute has the wrong content") {
      val x = the [IllegalArgumentException] thrownBy parse("<dto i='hello'><s>One</s><l>123</l><s>Two</s></dto>", dto1proto.reader)
      x.getMessage should include ("Expecting a signed 32-bit decimal integer")
      x.getMessage should include ("hello")
      x.getMessage should include ("at 1:16")
    }
  }
}