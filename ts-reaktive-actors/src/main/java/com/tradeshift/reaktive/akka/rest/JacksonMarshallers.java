package com.tradeshift.reaktive.akka.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.server.Marshaller;
import akka.http.javadsl.server.Unmarshaller;

/**
 * Various marshallers that help marshalling to/from JSON using Jackson. It configures
 * the default object mapper with more lenient settings than the default mapper akka's 
 * Jackson support uses.
 */
public class JacksonMarshallers {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    static {
        // Globally disable Jackson picking up any fields or getters that it finds.
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
            .with(JsonAutoDetect.Visibility.NONE)
            .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY));
        
        // Allow incoming JSONs to have more fields than we map, which will be ignored.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public static <T> Marshaller<T, RequestEntity> toJSON() {
        return Jackson.marshaller(mapper);
    }
    
    public static final Unmarshaller<HttpEntity,JsonNode> asJsonNode = as(JsonNode.class);
    
    public static final <T> Unmarshaller<HttpEntity,T> as(Class<T> type) { 
        return Jackson.unmarshaller(mapper, type);
    }
}
