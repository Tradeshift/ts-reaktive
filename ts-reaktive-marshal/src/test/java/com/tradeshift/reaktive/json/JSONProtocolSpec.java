package com.tradeshift.reaktive.json;

import static com.tradeshift.reaktive.json.JSONEvent.END_ARRAY;
import static com.tradeshift.reaktive.json.JSONEvent.END_OBJECT;
import static com.tradeshift.reaktive.json.JSONEvent.START_ARRAY;
import static com.tradeshift.reaktive.json.JSONEvent.START_OBJECT;
import static com.tradeshift.reaktive.json.JSONProtocol.anyField;
import static com.tradeshift.reaktive.json.JSONProtocol.array;
import static com.tradeshift.reaktive.json.JSONProtocol.field;
import static com.tradeshift.reaktive.json.JSONProtocol.integerValue;
import static com.tradeshift.reaktive.json.JSONProtocol.longValue;
import static com.tradeshift.reaktive.json.JSONProtocol.object;
import static com.tradeshift.reaktive.json.JSONProtocol.stringValue;
import static com.tradeshift.reaktive.marshal.Protocol.alternatively;
import static com.tradeshift.reaktive.marshal.Protocol.foldLeft;
import static com.tradeshift.reaktive.marshal.Protocol.hashMap;
import static com.tradeshift.reaktive.marshal.Protocol.option;
import static com.tradeshift.reaktive.marshal.Protocol.vector;
import static javaslang.control.Option.none;
import static javaslang.control.Option.some;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.only;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.Regex;
import com.tradeshift.reaktive.json.JSONEvent.FieldName;
import com.tradeshift.reaktive.json.JSONEvent.NumericValue;
import com.tradeshift.reaktive.json.JSONEvent.StringValue;
import com.tradeshift.reaktive.json.jackson.Jackson;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;

import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;
import javaslang.control.Try;

@RunWith(CuppaRunner.class)
public class JSONProtocolSpec {{
    final Jackson jackson = new Jackson();
    
    describe("a JSONProtocol for a type with a required and repeated field", () -> {
        Protocol<JSONEvent, DTO1> proto = 
            object(
                field("l", longValue),
                option(
                    field("i", integerValue)
                ),
                field("s", 
                    array(
                        vector(
                            stringValue 
                        )
                    )
                ),
                (l, i, s) -> new DTO1(l, i, s),
                t -> t.getL(),
                t -> t.getI(),
                t -> t.getS()
            );        
        
        it("should unmarshal a JSON document containing all fields", () -> {
            Reader<JSONEvent, DTO1> r = proto.reader();
            
            Try<DTO1> emitted = null;
            for (JSONEvent evt: Arrays.asList(
                START_OBJECT,
                new FieldName("l"), new NumericValue("42"),
                new FieldName("i"), new NumericValue("84"),
                new FieldName("s"), 
                    START_ARRAY,
                    new StringValue("Error 1"),
                    new StringValue("Error 2"),
                    END_ARRAY,
                END_OBJECT
            )) {
                emitted = r.apply(evt);
            }
            
            assertThat(emitted).isEqualTo(Try.success(new DTO1(42, Option.of(84), Vector.of("Error 1", "Error 2"))));
        });

        it("should unmarshal a JSON document missing an optional field", () -> {
            Reader<JSONEvent, DTO1> r = proto.reader();
            
            Try<DTO1> emitted = null;
            for (JSONEvent evt: Arrays.asList(
                START_OBJECT,
                new FieldName("l"), new NumericValue("42"),
                END_OBJECT
            )) {
                emitted = r.apply(evt);
            }
            
            assertThat(emitted).isEqualTo(Try.success(new DTO1(42, Option.none(), Vector.empty())));
        });
        
        it("should fail when an optional field has an incompatible value", () -> {
            assertThatThrownBy(() -> {
                jackson.parse("{\"l\":42, \"i\":\"No number!\"}", proto.reader());
            }).isInstanceOf(IllegalArgumentException.class);
        });
        
        it("should marshal a complete object into JSON", () -> {
            List<JSONEvent> events = proto.writer().apply(
                new DTO1(42, Option.some(84), Vector.of("Error 1", "Error 2"))
            ).collect(Collectors.toList());
            
            assertThat(events).containsExactly(
                START_OBJECT,
                new FieldName("l"), new NumericValue("42"),
                new FieldName("i"), new NumericValue("84"),
                new FieldName("s"), 
                    START_ARRAY,
                    new StringValue("Error 1"),
                    new StringValue("Error 2"),
                    END_ARRAY,
                END_OBJECT
            );
        });
        
        it("should marshal an object missing optional fields into JSON", () -> {
            List<JSONEvent> events = proto.writer().apply(new DTO1(42, Option.none(), Vector.empty())).collect(Collectors.toList());
            
            assertThat(events).containsExactly(
                START_OBJECT,
                new FieldName("l"), new NumericValue("42"),
                END_OBJECT
            );
        });
        
        it("should ignore an object or array in place where a string value is expected", () -> {
            assertThat(jackson.parse("{\"l\":42,\"i\":{}}", proto.reader()).findFirst()).contains(
                new DTO1(42, none(), Vector.empty()));
            assertThat(jackson.parse("{\"l\":42,\"i\":{\"i\":1}}", proto.reader()).findFirst()).contains(
                new DTO1(42, none(), Vector.empty()));
            assertThat(jackson.parse("{\"l\":42,\"i\":[]}", proto.reader()).findFirst()).contains(
                new DTO1(42, none(), Vector.empty()));
        });        
    });
    
    describe("a JSONProtocol explicitly marking an array field as optional", () -> {
        Protocol<JSONEvent, Option<Seq<String>>> proto = object(
            option(
                field("s", 
                    array(
                        vector(stringValue)
                    )
                )
            )
        );
        
        it("detects a present field but empty array correctly", () -> {
            assertThat(jackson.parse("{\"s\":[]}", proto.reader()).findFirst()).contains(
                Option.some(Vector.empty()));
        });
        
        it("detects an absent field correctly", () -> {
            assertThat(jackson.parse("{}", proto.reader()).findFirst()).contains(
                Option.none());
        });
        
    });
    
    describe("A JSONProtocol with nested arrays represented as nested seqs", () -> {
        ObjectProtocol<Seq<Seq<Integer>>> proto = object(
            field("a",
                array(
                    vector(
                        array(
                            vector(integerValue)
                        )
                    )
                )
            )
        );
        
        it("should unmarshal correctly", () -> {
            assertThat(jackson.parse("{\"a\":[[1,2],[3,4]]}", proto.reader()).findFirst())
                .contains(Vector.of(Vector.of(1,2), Vector.of(3,4)));
            
        });
        
        it("should marshal correctly", () -> {
            assertThat(jackson.write(Vector.of(Vector.of(1,2), Vector.of(3,4)), proto.writer()))
                .isEqualTo("{\"a\":[[1,2],[3,4]]}");
        });
    });
    
    describe("a JSONProtocol for reading an array root", () -> {
        ReadProtocol<JSONEvent, DTO2> proto = array(
            object(
                option(field("i", integerValue)),
                i -> new DTO2(Option.none(), i)
                )
            );
        
        it("should yield an element for each matched object in the array", () -> {
            Vector<DTO2> result = jackson.parse("["
                + "{\"i\":1},"
                + "{\"i\":2},"
                + "{\"i\":3}"
                + "]", proto.reader()).collect(Vector.collector());
            
            assertThat(result).containsExactly(
                new DTO2(none(), some(1)),
                new DTO2(none(), some(2)),
                new DTO2(none(), some(3)));
        });
    });
    
    describe("a JSONProtocol with several alternatives", () -> {
        
        ReadProtocol<JSONEvent, DTO2> proto = alternatively(
            object(
                option(field("i", integerValue)),
                i -> new DTO2(Option.none(), i)
            ).having(field("alternative", integerValue), 1),
            
            object(
                option(field("d", object(
                    field("l", longValue),
                    l -> new DTO1(l, Option.none(), Vector.empty())
                ))),
                o -> new DTO2(o, Option.none())
            ).having(field("alternative", integerValue), 2)
        );        
        
        it("should pick the right alternative when 'having' is matched", () -> {
            Reader<JSONEvent, DTO2> r = proto.reader();
            
            assertThat(jackson.parse("{\"alternative\":1,\"i\":42}", r).findFirst()).contains(
                new DTO2(none(), some(42)));
            assertThat(jackson.parse("{\"alternative\":2}", r).findFirst()).contains(
                new DTO2(none(), none()));
            assertThat(jackson.parse("{\"alternative\":2,\"d\":{\"l\":42}}", r).findFirst()).contains(
                new DTO2(some(new DTO1(42l, none(), Vector.empty())), none()));
        });
        
        it("fail with error message when encountering an object or array in place where a constant is expected", () -> {
            assertThatThrownBy(() -> jackson.parse("{\"alternative\":{},\"i\":42}", proto.reader()))
                .hasMessageContaining("alternative:")
                .hasMessageContaining("1")
                .hasMessageContaining("alternative")
                .hasMessageContaining("2");
            assertThatThrownBy(() -> jackson.parse("{\"alternative\":{\"alternative\":1},\"i\":42}", proto.reader()))
                .hasMessageContaining("alternative:")
                .hasMessageContaining("1")
                .hasMessageContaining("alternative")
                .hasMessageContaining("2");
            assertThatThrownBy(() -> jackson.parse("{\"alternative\":[],\"i\":42}", proto.reader()))
                .hasMessageContaining("alternative:")
                .hasMessageContaining("1")
                .hasMessageContaining("alternative")
                .hasMessageContaining("2");
        });        
    });
    
    describe("a JSONProtocol with a condition on a required sub-object that is also mapped", () -> {
        ObjectProtocol<DTO2> proto = object(
            field("d", 
                object(
                    field("l", longValue),
                    (Long l) -> new DTO1(l, Option.none(), Vector.empty()),
                    t -> t.getL()
                )
                .having(field("hello", stringValue), "world")
            ),
            o -> new DTO2(some(o), none()),
            t -> t.getD().get()
        );
        
        it("should unmarshal JSON where the condition is present", () -> {
            assertThat(jackson.parse("{\"d\":{\"l\":42,\"hello\":\"world\"}}", proto.reader()).findFirst()).contains(
                new DTO2(some(new DTO1(42, none(), Vector.empty())), none()));            
        });
        
        it("should fail if the condition is not present", () -> {
            assertThatThrownBy(() -> jackson.parse("{\"d\":{\"l\":42,\"hello\":\"mars\"}}", proto.reader()))
                .hasMessageContaining("hello: (string) with value world");
        });
        
        it("should write the conditon out when marshalling JSON", () -> {
            assertThat(jackson.write(new DTO2(some(new DTO1(42, none(), Vector.empty())), none()), proto.writer()))
            .isEqualTo("{\"d\":{\"hello\":\"world\",\"l\":42}}");
        });
    });
    
    describe("a JSONProtocol with a string field matching a regex", () -> {
        Protocol<JSONEvent, UUID> proto = 
            object(
                field("id", stringValue.matching(Regex.aUUID, uuid -> uuid.toString())),
                uuid -> uuid,
                uuid -> uuid
            );
        
        it("should unmarshal JSON where the regex matches", () -> {
            assertThat(jackson.parse("{\"id\":\"0c5317ef-fb33-446d-b02e-8a73e9d033c4\"}", proto.reader()).findFirst())
                .contains(UUID.fromString("0c5317ef-fb33-446d-b02e-8a73e9d033c4"));            
        });
        
        it("should fail JSON where the regex does not match", () -> {
            assertThatThrownBy(() -> jackson.parse("{\"id\":\"0c5317ef-fb33-446d-b02e-rabbits\"}", proto.reader()))
                .hasMessageContaining("[0-9"); // the regex should be in the error message
        });
        
        it("should write objects using the given toString function", () -> {
            assertThat(jackson.write(UUID.fromString("0c5317ef-fb33-446d-b02e-8a73e9d033c4"), proto.writer()))
                .isEqualTo("{\"id\":\"0c5317ef-fb33-446d-b02e-8a73e9d033c4\"}");
        });
    });
    
    describe("a JSONProtocol matching a JSON object into a Java map of strings", () -> {
        ObjectProtocol<Map<String, String>> proto = object(
            hashMap(
                anyField(
                    stringValue
                )
            )
        );
        
        it("should read correctly", () -> {
            assertThat(jackson.parse("{\"hello\":\"world\",\"foo\":\"bar\"}", proto.reader()).findFirst())
                .contains(HashMap.of("hello", "world").put("foo", "bar"));
        });
        
        it("should write correctly", () -> {
            assertThat(jackson.write(HashMap.of("hello", "world").put("foo", "bar"), proto.writer()))
                .isEqualTo("{\"foo\":\"bar\",\"hello\":\"world\"}");
        });
    });
    
    describe("a JSONProtocol matching a JSON object of arbitrary keys with array values", () -> {
        ObjectProtocol<Map<String, Seq<Integer>>> proto = object(
            hashMap(
                anyField(
                    array(
                        vector(integerValue)
                    )
                )
            )
        );
        
        it("should read correctly", () -> {
            assertThat(jackson.parse("{\"hello\":[1,2],\"foo\":[3,4]}", proto.reader()).findFirst())
                .contains(HashMap.<String,Seq<Integer>>of("hello", Vector.of(1,2)).put("foo", Vector.of(3,4)));
        });
        
        it("should write correctly", () -> {
            assertThat(jackson.write(HashMap.<String,Seq<Integer>>of("hello", Vector.of(1,2)).put("foo", Vector.of(3,4)), proto.writer()))
                .isEqualTo("{\"foo\":[3,4],\"hello\":[1,2]}");
        });
        
    });
    
    describe("a JSONProtocol folding all array elements into a single value", () -> {
        ReadProtocol<JSONEvent, Integer> proto = array(
            foldLeft(
            integerValue,
            () -> 0,
            (i1, i2) -> i1 + i2
        ));
        
        it("should invoke the fold function on all elements", () -> {
            assertThat(jackson.parse("[1,2,3]", proto.reader()).findFirst()).contains(6);
        });
        
        it("should yield initial on an empty array", () -> {
            assertThat(jackson.parse("[]", proto.reader()).findFirst()).contains(0);
        });
        
        it("should fail early if an item doesn't fit", () -> {
            assertThatThrownBy(() -> 
                jackson.parse("[1,false,3]", proto.reader()).findFirst()
            ).hasMessageContaining("false");
        });
    });
}}
