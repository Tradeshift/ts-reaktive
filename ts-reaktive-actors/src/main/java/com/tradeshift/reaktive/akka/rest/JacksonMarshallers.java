package com.tradeshift.reaktive.akka.rest;

import static com.tradeshift.reaktive.Lambdas.unchecked;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.server.Marshaller;
import akka.http.javadsl.server.Unmarshaller;

/**
 * Various marshallers that help marshalling to/from JSON using Jackson
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
        return Marshaller.wrapEntity(unchecked(mapper::writeValueAsString), Marshaller.stringToEntity(), MediaTypes.APPLICATION_JSON);
    }
    
    public static final Unmarshaller<HttpEntity,JsonNode> asJsonNode =
        Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
                    .thenApply(s -> {
                        try {
                            return mapper.readTree(s);
                        } catch (JsonParseException x) {
                            throw new IllegalArgumentException(x);
                        } catch (IOException x) {
                            throw new IllegalStateException(x);
                        }
                    });}
