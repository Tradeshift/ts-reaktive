package com.tradeshift.reaktive.json.jackson;

import static javaslang.control.Option.some;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Option;
import javaslang.control.Try;

public class Jackson {
    private static final JsonFactory factory = new JsonFactory();
    
    public <T> String write(T obj, Writer<JSONEvent, T> writer) {
        StringWriter out = new StringWriter();
        try {
            JsonGenerator gen = factory.createGenerator(out);
            writer.apply(obj).forEach(evt -> {
                try {
                    if (evt == JSONEvent.START_OBJECT) {
                        gen.writeStartObject();
                    } else if (evt == JSONEvent.END_OBJECT) {
                        gen.writeEndObject();
                    } else if (evt == JSONEvent.START_ARRAY) {
                        gen.writeStartArray();
                    } else if (evt == JSONEvent.END_ARRAY) {
                        gen.writeEndArray();
                    } else if (evt == JSONEvent.TRUE) {
                        gen.writeBoolean(true);
                    } else if (evt == JSONEvent.FALSE) {
                        gen.writeBoolean(false);
                    } else if (evt == JSONEvent.NULL) {
                        gen.writeNull();
                    } else if (evt instanceof JSONEvent.FieldName) {
                        gen.writeFieldName(JSONEvent.FieldName.class.cast(evt).getName());
                    } else if (evt instanceof JSONEvent.StringValue) {
                        gen.writeString(JSONEvent.StringValue.class.cast(evt).getValueAsString());
                    } else if (evt instanceof JSONEvent.NumericValue) {
                        gen.writeNumber(JSONEvent.NumericValue.class.cast(evt).getValueAsString());
                    } 
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            gen.close();
            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public <T> Stream<T> parse(String s, Reader<JSONEvent, T> reader) {
        try {
            return parse(factory.createParser(s), reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public <T> Stream<T> parse(JsonParser input, Reader<JSONEvent, T> reader) {
        reader.reset();
        
        Iterator<T> iterator = new Iterator<T>() {
            private Option<T> next = parse();
            
            private Option<T> parse() {
                for (Option<JSONEvent> evt = nextEvent(); evt.isDefined(); evt = nextEvent()) {
                    Try<T> read = reader.apply(evt.get());
                    if (read.isSuccess()) {
                        return read.toOption();
                    } else if (read.isFailure() && read != ReadProtocol.NONE) {
                        throw (RuntimeException) read.failed().get();
                    }                    
                }
                return Option.none();
            }
            
            private Option<JSONEvent> nextEvent() {
                try {
                    while (input.nextToken() != null) {
                        switch (input.getCurrentToken()) {
                        case START_OBJECT: return some(JSONEvent.START_OBJECT);
                        case END_OBJECT: return some(JSONEvent.END_OBJECT);
                        case START_ARRAY: return some(JSONEvent.START_ARRAY);
                        case END_ARRAY: return some(JSONEvent.END_ARRAY);
                        case VALUE_FALSE: return some(JSONEvent.FALSE);
                        case VALUE_TRUE: return some(JSONEvent.TRUE);
                        case VALUE_NULL: return some(JSONEvent.NULL);
                        case FIELD_NAME: return some(new JSONEvent.FieldName(input.getCurrentName()));
                        case VALUE_NUMBER_FLOAT: return some(new JSONEvent.NumericValue(input.getValueAsString()));
                        case VALUE_NUMBER_INT: return some(new JSONEvent.NumericValue(input.getValueAsString()));
                        case VALUE_STRING: return some(new JSONEvent.StringValue(input.getValueAsString()));
                        default: throw new IllegalArgumentException("Unexpected token " + input.getCurrentToken() + " at " + input.getCurrentLocation());
                        }
                    }
                    return Option.none(); // end of stream
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            
            @Override
            public boolean hasNext() {
                return next.isDefined();
            }

            @Override
            public T next() {
                T elmt = next.get();
                next = parse();
                return elmt;
            }
        };
        
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
            false);
    }
}
