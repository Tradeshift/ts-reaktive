package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.marshal.Protocol.vector;
import static com.tradeshift.reaktive.xml.JAXBProtocol.jaxbType;
import static com.tradeshift.reaktive.xml.XMLProtocol.qname;
import static com.tradeshift.reaktive.xml.XMLProtocol.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.marshal.Protocol;

import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class JAXBProtocolSpec {
    @XmlRootElement(name = "jaxbDto")
    private static class JAXBDTO {
        @XmlAttribute(name = "i")
        private final int i;
        
        @XmlValue
        private final String b;
        
        @SuppressWarnings("unused") // for JAXB
        private JAXBDTO() {
            i = 0;
            b = null;
        }

        public JAXBDTO(int i, String b) {
            this.i = i;
            this.b = b;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((b == null) ? 0 : b.hashCode());
            result = prime * result + i;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            JAXBDTO other = (JAXBDTO) obj;
            if (b == null) {
                if (other.b != null)
                    return false;
            } else if (!b.equals(other.b))
                return false;
            if (i != other.i)
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "JAXBDTO [i=" + i + ", b=" + b + "]";
        }
    }
    
    Stax stax = new Stax();
{
    describe("JAXBProtocol", () -> {
        when("nested inside another non-jaxb protocol", () -> {
            Protocol<XMLEvent, Seq<JAXBDTO>> proto = tag(qname("root"),
                vector(
                    jaxbType(JAXBDTO.class)
                )
            );
            
            it("should write an instance as nested correctly", () -> {
                assertThat(
                    stax.writeAsString(Vector.of(new JAXBDTO(1, "one"), new JAXBDTO(2, "two")), proto.writer())
                ).isEqualTo("<root><jaxbDto i=\"1\">one</jaxbDto><jaxbDto i=\"2\">two</jaxbDto></root>");
            });
            
            it("should read a nested instance correctly", () -> {
                Seq<JAXBDTO> list = stax.parse("<root><jaxbDto i=\"1\">one</jaxbDto><jaxbDto i=\"2\">two</jaxbDto></root>", proto.reader()).findFirst().get();
                assertThat(list).containsExactly(new JAXBDTO(1, "one"), new JAXBDTO(2, "two"));
            });
        });
        
        when("operating as the root protocol", () -> {
            Protocol<XMLEvent, JAXBDTO> proto = jaxbType(JAXBDTO.class);
            
            it("should write an instance correctly", () -> {
                assertThat(
                    stax.writeAsString(new JAXBDTO(1, "one"), proto.writer())
                ).isEqualTo("<jaxbDto i=\"1\">one</jaxbDto>");
            });
            
            it("should read an instance correctly", () -> {
                assertThat(
                    stax.parse("<jaxbDto i=\"1\">one</jaxbDto>", proto.reader()).findFirst().get()
                ).isEqualTo(new JAXBDTO(1, "one"));
            });
        });
    });
}}
