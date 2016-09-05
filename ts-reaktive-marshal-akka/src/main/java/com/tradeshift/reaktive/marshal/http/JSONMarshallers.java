package com.tradeshift.reaktive.marshal.http;

import static com.tradeshift.reaktive.akka.AsyncUnmarshallers.entityToStream;

import com.tradeshift.reaktive.akka.AsyncUnmarshallers;
import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.stream.ActsonReader;
import com.tradeshift.reaktive.marshal.stream.JacksonWriter;
import com.tradeshift.reaktive.marshal.stream.ProtocolReader;
import com.tradeshift.reaktive.marshal.stream.ProtocolWriter;

import akka.NotUsed;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

public class JSONMarshallers {
    /**
     * Returns a marshaller that will render a stream of T into a JSON response using [protocol].
     */
    public static <T> Marshaller<Source<T,?>, RequestEntity> sourceToJSON(WriteProtocol<JSONEvent, T> protocol) {
        return HttpStreamingMarshallers
            .sourceToEntity(MediaTypes.APPLICATION_JSON.toContentType())
            .compose((Source<T,?> source) -> source
                .via(ProtocolWriter.flow(protocol))
                .via(JacksonWriter.flow()));
    }
 
    /**
     * Returns a marshaller that will render a T into a JSON response using [protocol].
     */
    public static <T> Marshaller<T, RequestEntity> toJSON(WriteProtocol<JSONEvent, T> protocol) {
        return sourceToJSON(protocol).compose((T t) -> Source.single(t));
    }
    
    /**
     * Returns an unmarshaller that will read a JSON request into a stream of T using [protocol].
     */
    public static <T> Unmarshaller<HttpEntity, Source<T,NotUsed>> sourceFromJSON(ReadProtocol<JSONEvent, T> protocol) {
        return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, entityToStream().thenApply(source -> source
            .via(ActsonReader.instance)
            .via(ProtocolReader.of(protocol))));
    }
    
    /**
     * Returns an unmarshaller that will read a JSON request into a T using [protocol].
     */
    public static <T> Unmarshaller<HttpEntity, T> fromJSON(ReadProtocol<JSONEvent, T> protocol) {
        Unmarshaller<HttpEntity, Source<T, NotUsed>> streamUnmarshaller = sourceFromJSON(protocol);
        
        return AsyncUnmarshallers.<HttpEntity, T>withMaterializer((ctx, mat, entity) -> {
            return streamUnmarshaller.unmarshall(entity, ctx, mat).thenCompose(src -> src.runWith(Sink.head(), mat));
        });
    }
}
