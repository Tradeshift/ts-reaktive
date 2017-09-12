package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.marshal.Protocol.anyOf;
import static com.tradeshift.reaktive.marshal.Protocol.arrayList;
import static com.tradeshift.reaktive.marshal.Protocol.forEach;
import static com.tradeshift.reaktive.marshal.Protocol.hashMap;
import static com.tradeshift.reaktive.marshal.Protocol.option;
import static com.tradeshift.reaktive.marshal.Protocol.vector;
import static com.tradeshift.reaktive.marshal.StringMarshallable.INTEGER;
import static com.tradeshift.reaktive.marshal.StringMarshallable.LONG;
import static com.tradeshift.reaktive.xml.XMLProtocol.anyAttribute;
import static com.tradeshift.reaktive.xml.XMLProtocol.anyTag;
import static com.tradeshift.reaktive.xml.XMLProtocol.anyTagWithAttribute;
import static com.tradeshift.reaktive.xml.XMLProtocol.anyTagWithBody;
import static com.tradeshift.reaktive.xml.XMLProtocol.attribute;
import static com.tradeshift.reaktive.xml.XMLProtocol.body;
import static com.tradeshift.reaktive.xml.XMLProtocol.qname;
import static com.tradeshift.reaktive.xml.XMLProtocol.tag;
import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.ReadProtocol;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

@RunWith(CuppaRunner.class)
public class XMLProtocolSpec {{
    final Stax stax = new Stax();
    
    final Protocol<XMLEvent,DTO1> dto1proto =
        tag(qname("dto"),
            tag(qname("l"),
                body.as(LONG)
            ),
            option(
                attribute("i").as(INTEGER)
            ),
            vector(
                tag(qname("s"),
                    body
                )
            ),
            DTO1::new,
            dto -> dto.getL(),
            dto -> dto.getI(),
            dto -> dto.getS()
        );
    
    describe("An XMLProtocol mapping both attributes and child tags ", () -> {
        it("should read a document from XML where the attribute and child tags are absent", () -> {
            assertThat(
                stax.parse("<dto><l>123</l></dto>", dto1proto.reader()).findFirst()
            ).contains(new DTO1(123, none(), Vector.empty()));
        });
        
        it("should read a complete document from XML", () -> {
            assertThat(
                stax.parse("<dto i='42'><s>One</s><l>123</l><s>Two</s></dto>", dto1proto.reader()).findFirst()
            ).contains(new DTO1(123, some(42), Vector.of("One", "Two")));
        });
        
        it("should fail if an optional attribute has the wrong content", () -> {
            assertThatThrownBy(() -> {
                stax.parse("<dto i='hello'><s>One</s><l>123</l><s>Two</s></dto>", dto1proto.reader());
            })
            .hasMessageContaining("Expecting a signed 32-bit decimal integer")
            .hasMessageContaining("hello")
            .hasMessageContaining("at 1:16")
            .isInstanceOf(IllegalArgumentException.class);
        });
        
        it("should render an incomplete DTO into XML", () -> {
            assertThat(
                stax.writeAsString(new DTO1(123, none(), Vector.empty()), dto1proto.writer())
            ).isEqualTo("<dto><l>123</l></dto>");
        });
        
        it("should render a complete DTO into XML", () -> {
            assertThat(
                stax.writeAsString(new DTO1(123, some(42), Vector.of("One", "Two")), dto1proto.writer())
            ).isEqualTo("<dto i=\"42\"><l>123</l><s>One</s><s>Two</s></dto>");
        });
        
        it("should be convertable to and from a new data type using map", () -> {
            Protocol<XMLEvent,String> mapped = dto1proto.map(dto -> dto.getS().head(), s -> new DTO1(123, none(), Vector.of(s)));
            assertThat(
                stax.writeAsString("hello", mapped.writer())
            ).isEqualTo("<dto><l>123</l><s>hello</s></dto>");
            assertThat(
                stax.parse("<dto i='42'><s>One</s><l>123</l><s>Two</s></dto>", mapped.reader()).findFirst()
            ).contains("One");
        });
    });

    describe("An XMLProtocol that relies on side-effects to allow streamed parsing", () -> {
        List<DTO1> received = new ArrayList<>();
        
        TagReadProtocol<Void> streamedProto =
            tag(qname("root"),
                forEach(dto1proto, received::add)
            );
        
        it("should invoke the side effect for each parsed element", () -> {
            received.clear();
            stax.parse("<root><dto><l>1</l></dto><dto><l>2</l></dto><dto><l>3</l></dto></root>", streamedProto.reader());
            assertThat(received).containsExactly(
                new DTO1(1, none(), Vector.empty()),
                new DTO1(2, none(), Vector.empty()),
                new DTO1(3, none(), Vector.empty())
            );
        });
    });
    
    describe("An XMLProtocol for a tag that maps all sub tag's content into a map of strings", () -> {
        TagProtocol<Map<String, String>> proto = tag(qname("properties"),
            hashMap(
                anyTagWithBody.asLocalName()
            )
        );
        
        it("should write correctly", () -> {
            assertThat(
                stax.writeAsString(HashMap.of("hello", "world").put("good", "stuff"), proto.writer())
            ).isEqualTo("<properties><hello>world</hello><good>stuff</good></properties>");
        });
        
        it("should read correctly", () -> {
            assertThat(
                stax.parse("<properties><hello>world</hello><good>stuff</good></properties>", proto.reader()).findFirst()
            ).contains(HashMap.of("hello", "world").put("good", "stuff"));
        });
    });
    
    describe("An XMLProtocol for a tag that maps all sub tag's 'value' attributes into a map of strings", () -> {
        TagProtocol<Map<String, String>> proto = tag(qname("properties"),
            hashMap(
                anyTagWithAttribute("value").asLocalName()
            )
        );
        
        it("should write correctly", () -> {
            assertThat(
                stax.writeAsString(HashMap.of("hello", "world").put("good", "stuff"), proto.writer())
            ).isEqualTo("<properties><hello value=\"world\"></hello><good value=\"stuff\"></good></properties>");
        });
        
        it("should read correctly", () -> {
            assertThat(
                stax.parse("<properties><hello value=\"world\"/><good value=\"stuff\"/></properties>", proto.reader()).findFirst()
            ).contains(HashMap.of("hello", "world").put("good", "stuff"));
        });
    });
    
    describe("An XMLProtocol for a tag that maps all attributes into a map of strings", () -> {
        TagProtocol<Map<String, String>> proto = tag(qname("properties"),
            hashMap(
                anyAttribute.asLocalName()
            )
        );
        
        it("should write correctly", () -> {
            assertThat(
                stax.writeAsString(HashMap.of("hello", "world").put("good", "stuff"), proto.writer())
            ).isEqualTo("<properties hello=\"world\" good=\"stuff\"></properties>");
        });
        
        it("should read correctly", () -> {
            assertThat(
                stax.parse("<properties hello=\"world\" good=\"stuff\"/>", proto.reader()).findFirst()
            ).contains(HashMap.of("hello", "world").put("good", "stuff"));
        });
    });
    
    describe("An XMLProtocol with several alternatives", () -> {
        TagProtocol<DTO1> protoV1 = tag(qname("dto"),
            tag(qname("l"),
                body.as(LONG)
            ),
            l -> new DTO1(l, Option.none(), Vector.empty()),
            dto -> dto.getL()
        );
        
        TagProtocol<DTO1> protoV2 = tag(qname("dtov2"),
            tag(qname("l"),
                body.as(LONG)
            ),
            l -> new DTO1(l, Option.none(), Vector.empty()),
            dto -> dto.getL()
        );
        
        Protocol<XMLEvent,DTO1> proto = anyOf(
            protoV1.having(attribute("version"), "1"),
            protoV2.having(attribute("version"), "2")
        );

        it("should fail if the condition is absent", () -> {
            assertThatThrownBy(() ->
                stax.parse("<dto><l>42</l></dto>", proto.reader()).findFirst()
            ).hasMessageContaining("must have @version");
        });
        
        it("should fail if the condition is wrong", () -> {
            assertThatThrownBy(() ->
                stax.parse("<dto version='3'><l>42</l></dto>", proto.reader()).findFirst()
            ).hasMessageContaining("must have @version");
        });
        
        it("should unmarshal a first alternative correctly", () -> {
            assertThat(
                stax.parse("<dto version='1'><l>42</l></dto>", proto.reader()).findFirst()
            ).contains(new DTO1(42, Option.none(), Vector.empty()));
        });
        
        it("should unmarshal a second alternative correctly", () -> {
            assertThat(
                stax.parse("<dtov2 version='2'><l>42</l></dtov2>", proto.reader()).findFirst()
            ).contains(new DTO1(42, Option.none(), Vector.empty()));
        });
        
        it("should pick the first alternative when writing", () -> {
            assertThat(
                stax.writeAsString(new DTO1(42, Option.none(), Vector.empty()), proto.writer())
            ).isEqualTo("<dto version=\"1\"><l>42</l></dto>");
        });
    });
    
    describe("An XMLProtocol with several alternatives that differ by extension", () -> {
        TagProtocol<DTO1> protoV2 = tag(qname("dto"),
            tag(qname("l"),
                body.as(LONG)
            ),
            l -> new DTO1(l, Option.none(), Vector.of("v2")),
            dto -> dto.getL()
        );
        
        TagProtocol<DTO1> protoV1 = tag(qname("dto"),
            tag(qname("l"),
                body.as(LONG)
            ),
            l -> new DTO1(l, Option.none(), Vector.of("v1")),
            dto -> dto.getL()
        );
        
        Protocol<XMLEvent,DTO1> proto = anyOf(
            protoV2.having(
                attribute("version"), "2"
            ),
            protoV1
        );

        it("should unmarshal a first alternative correctly", () -> {
            assertThat(
                stax.parse("<dto version='2'><l>42</l></dto>", proto.reader()).findFirst()
            ).contains(new DTO1(42, Option.none(), Vector.of("v2")));
        });
        
        it("should unmarshal a second alternative correctly", () -> {
            assertThat(
                stax.parse("<dto><l>42</l></dto>", proto.reader()).findFirst()
            ).contains(new DTO1(42, Option.none(), Vector.of("v1")));
        });
    });
    
    describe("An XMLProtocol that deals with plain java ArrayList", () -> {
        Protocol<XMLEvent,List<String>> proto = tag(qname("list"),
            arrayList(
                tag(qname("item"), body)
            )
        );
        
        it("should marshal several items correctly", () -> {
            ArrayList<String> list = new ArrayList<>();
            list.add("hello");
            list.add("world");
            assertThat(
                stax.writeAsString(list, proto.writer())
            ).isEqualTo("<list><item>hello</item><item>world</item></list>");
        });
        
        it("should unmarshal several items correctly", () -> {
            List<String> list = stax.parse("<list><item>hello</item><item>world</item></list>", proto.reader()).findFirst().get();
            assertThat(list).containsExactly("hello", "world");
        });
    });
    
    describe("An XMLProtocol that describes strings as nested tags", () -> {
        Protocol<XMLEvent,String> proto = tag(qname("root"),
            tag(qname("element"),
                body
            )
            .having(
                attribute("important"), "true"
            )
        );
        
        it("should unmarshal multiple tags into multiple strings", () -> {
            List<String> list = stax.parse("<root><element important='true'>hello</element><element important='true'>world</element></root>", proto.reader()).collect(Collectors.toList());
            assertThat(list).containsExactly("hello", "world");
        });
        
        it("should marshal multiple strings into a full XML document", () -> {
            assertThat(
                stax.writeAllAsString(Arrays.asList("hello", "world").stream(), proto.writer())
            ).isEqualTo("<root><element important=\"true\">hello</element><element important=\"true\">world</element></root>");
        });
    });
    
    describe("An XMLProtocol for anyTag matching inner XML events", () -> {
        
        ReadProtocol<XMLEvent, XMLEvent> proto = anyTag( // accept any root tag
            tag(qname("innerTag"))
        );
        
        it("should extract the matched inner tags as events", () -> {
            List<XMLEvent> list = stax.parse("<root><tag></tag><innerTag>hello</innerTag></root>", proto.reader()).collect(Collectors.toList());
            assertThat(list).hasSize(3);
            assertThat(list.get(0).asStartElement().getName().getLocalPart()).isEqualTo("innerTag");
            assertThat(list.get(1).asCharacters().getData()).isEqualTo("hello");
            assertThat(list.get(2).asEndElement().getName().getLocalPart()).isEqualTo("innerTag");
        });
    });
    
    describe("An XMLProtocol for anyTag matching an inner protocol", () -> {
        ReadProtocol<XMLEvent, Option<String>> proto = anyTag(
            option(
                tag(qname("innerTag"),
                    body
                )
            )
        );
        
        it("should extract matched inner tags, ignoring the outer tag", () -> {
            assertThat(
                stax.parse("<root><tag>foo</tag><innerTag>hello</innerTag></root>", proto.reader()).findFirst()
            ).contains(Option.some("hello"));
        });
    });
}}
