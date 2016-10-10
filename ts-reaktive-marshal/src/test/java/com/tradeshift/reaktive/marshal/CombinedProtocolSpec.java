package com.tradeshift.reaktive.marshal;

import static com.tradeshift.reaktive.json.JSONProtocol.integerValue;
import static com.tradeshift.reaktive.json.JSONProtocol.stringValue;
import static com.tradeshift.reaktive.marshal.Protocol.combine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.json.jackson.Jackson;

import javaslang.collection.Seq;

@RunWith(CuppaRunner.class)
public class CombinedProtocolSpec {{
    describe("Protocol.allOf", () -> {
        Jackson jackson = new Jackson();
        
        it("should emit multiple results if multiple readers emit on the same event", () -> {
            ReadProtocol<JSONEvent, Seq<Integer>> protocol = combine(
                integerValue,
                integerValue.map(i -> i * 2)
            );
            
            assertThat(jackson.parse("42", protocol.reader()).findFirst().get()).containsExactly(42, 84);
        });
        
        it("should emit an event if one reader emits and another yields an error", () -> {
            ReadProtocol<JSONEvent, Seq<Object>> protocol = combine(
                stringValue.map(s -> (Object) s),
                integerValue.map(i -> (Object) i)
            );
            
            assertThat(jackson.parse("\"hello\"", protocol.reader()).findFirst().get()).containsExactly("hello");
        });
        
        it("should yield an error if all readers yield errors", () -> {
            ReadProtocol<JSONEvent, Seq<Integer>> protocol = combine(
                integerValue,
                integerValue.map(i -> i * 2)
            );
            
            assertThatThrownBy(() -> jackson.parse("\"hello\"", protocol.reader())).hasMessageContaining("Expecting signed 32-bit integer");
        });
    });
}}
