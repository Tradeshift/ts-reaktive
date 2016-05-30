package com.tradeshift.reaktive.assertj;

import java.util.Objects;
import org.assertj.core.api.AbstractAssert;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Performs AssertJ-style assertions on Jackson JsonNode objects
 */
public class JsonNodeAssertions extends AbstractAssert<JsonNodeAssertions,JsonNode> {
    protected JsonNodeAssertions(JsonNode actual) {
        super(actual, JsonNodeAssertions.class);
    }

    public static JsonNodeAssertions assertThat(JsonNode actual) {
        return new JsonNodeAssertions(actual);
    }

    /**
     * Asserts that the current JsonNode is a numeric value equal to [number].
     */
    public JsonNodeAssertions isInt(int number) {
        isNotNull();
        if (!Objects.equals(actual.asInt(), number)) {
            failWithMessage("Expected integer to be <%s> but was <%s>", number, actual.asInt());
        }
        return this;
    }

    /**
     * Asserts that the current JsonNode is a string value equal to [expected].
     */
    public JsonNodeAssertions isString(String expected) {
        isNotNull();
        if (!actual.isTextual()) {
            failWithMessage("Expected textual JSON node but was <%s>", actual);
        }
        if (!Objects.equals(actual.asText(), expected)) {
            failWithMessage("Expected string to be <%s> but was <%s>", expected, actual.asInt());            
        }
        return this;
    }

    /**
     * Asserts that the current JsonNode is a string value containing [expected].
     */
    public JsonNodeAssertions isStringContaining(String expected) {
        isNotNull();
        if (!actual.isTextual()) {
            failWithMessage("Expected textual JSON node but was <%s>", actual);
        }
        if (!actual.asText().contains(expected)) {
            failWithMessage("Expected string to contain <%s> but was <%s>", expected, actual.asInt());            
        }
        return this;
    }

    /**
     * Asserts that the current JsonNode is an array, where at least one element is equal to [str].
     */
    public JsonNodeAssertions isArrayWithString(String str) {
        isNotNull();
        if(!actual.isArray()){
            failWithMessage("Expected an array, but was <%s>", actual);
        }
        for (int i = 0; i < actual.size(); i++) {
            JsonNode elem = actual.get(i);
            if (elem.isTextual() && elem.asText().equals(str)) {
                return this;
            }
        }
        failWithMessage("Expected array to contain string <%s>, but was <%s>", str, actual);
        return this;
    }
    
    /**
     * Asserts that the current JsonNode is an array, where at least one element is equal to [expected].
     */
    public JsonNodeAssertions isArrayWithInt(int expected) {
        isNotNull();
        if(!actual.isArray()){
            failWithMessage("Expected an array, but was <%s>", actual);
        }
        for (int i = 0; i < actual.size(); i++) {
            JsonNode elem = actual.get(i);
            if (elem.isInt() && elem.asInt() == expected) {
                return this;
            }
        }
        failWithMessage("Expected array to contain int <%s>, but was <%s>", expected, actual);
        return this;
    }
    
    /**
     * Asserts that the current JsonNode is a string array, where at least one element is a string containing [str].
     */
    public JsonNodeAssertions isArrayWithStringContaining(String str) {
        isNotNull();
        if(!actual.isArray()){
            failWithMessage("Expected an array, but was <%s>", actual);
        }
        for (int i = 0; i < actual.size(); i++) {
            JsonNode elem = actual.get(i);
            if (elem.isTextual() && elem.asText().contains(str)) {
                return this;
            }
        }
        failWithMessage("Expected array to contain a string having <%s>, but was <%s>", str, actual);
        return this;
    }
}
