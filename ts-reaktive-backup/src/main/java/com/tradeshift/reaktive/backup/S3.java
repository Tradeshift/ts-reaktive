package com.tradeshift.reaktive.backup;

import static com.tradeshift.reaktive.marshal.Protocol.option;
import static com.tradeshift.reaktive.marshal.Protocol.vector;
import static com.tradeshift.reaktive.xml.XMLProtocol.body;
import static com.tradeshift.reaktive.xml.XMLProtocol.ns;
import static com.tradeshift.reaktive.xml.XMLProtocol.qname;
import static com.tradeshift.reaktive.xml.XMLProtocol.tag;
import static javaslang.control.Option.none;
import static javaslang.control.Option.some;
import static scala.compat.java8.FutureConverters.toJava;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.utils.UUIDs;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.StringMarshallable;
import com.tradeshift.reaktive.marshal.stream.AaltoReader;
import com.tradeshift.reaktive.marshal.stream.ProtocolReader;
import com.tradeshift.reaktive.protobuf.DelimitedProtobufFraming;
import com.tradeshift.reaktive.protobuf.EventEnvelopeSerializer;
import com.typesafe.config.Config;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.impl.model.JavaUri;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Host;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.http.scaladsl.model.ContentTypes;
import akka.http.scaladsl.model.Uri;
import akka.japi.Pair;
import akka.persistence.query.EventEnvelope2;
import akka.persistence.query.TimeBasedUUID;
import akka.stream.Materializer;
import akka.stream.alpakka.s3.BufferType;
import akka.stream.alpakka.s3.DiskBufferType;
import akka.stream.alpakka.s3.MemoryBufferType;
import akka.stream.alpakka.s3.S3Settings;
import akka.stream.alpakka.s3.auth.BasicCredentials;
import akka.stream.alpakka.s3.auth.CredentialScope;
import akka.stream.alpakka.s3.auth.Signer;
import akka.stream.alpakka.s3.auth.SigningKey;
import akka.stream.alpakka.s3.impl.CompleteMultipartUploadResult;
import akka.stream.alpakka.s3.impl.S3Headers;
import akka.stream.alpakka.s3.impl.S3Location;
import akka.stream.alpakka.s3.impl.S3Stream;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import javaslang.collection.Seq;
import javaslang.control.Option;
import scala.concurrent.Future;

/**
 * Small wrapper atop https://github.com/bluelabsio/s3-stream
 */
public class S3 {
    /**
     * S3 key names are {bucketKeyPrefix}{tag}{SEPARATOR}{time of first event}
     */
    private static final String SEPARATOR = "-from-";
    /**
     * The time of event in the S3 key name is formatted as uuuu_MM_dd_HH_mm_ss_SSS. This pattern was selected for several reasons:
     * - Encoding ":" yields URL encoding issues when calculating the S3 signature of the upload
     * - Leaving out "_" causes java's DateTimeFormatter to no longer being able to parse the date.
     * - DateTimeFormatter.ofPattern screws up royally when using "SSS"
     */
    private static final DateTimeFormatter FMT = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR_OF_ERA, 4)
        .appendLiteral('_')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendLiteral('_')
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendLiteral('_')
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral('_')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral('_')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendLiteral('_')
        .appendValue(ChronoField.MILLI_OF_SECOND, 3)
        .toFormatter().withZone(ZoneId.of("UTC"));

    static {
        Instant now = Instant.now();
        System.out.println(now.toEpochMilli());
        System.out.println(FMT.format(now));
    }
    
    private static final Logger log = LoggerFactory.getLogger(S3.class);
    
    private final String bucket;
    private final String bucketKeyPrefix;
    private final BasicCredentials creds;
    private final String region;
    private final Materializer materializer;
    private final Http http;
    private final ActorSystem system;
    private final EventEnvelopeSerializer serializer;
    private final S3Settings settings;

    public S3(ActorSystem system, Config config, Materializer materializer, Http http, EventEnvelopeSerializer serializer) {
        this.system = system;
        this.materializer = materializer;
        this.http = http;
        this.serializer = serializer;
        Config s3Config = config.getConfig("s3");
        bucket = s3Config.getString("bucket");
        String prefix = s3Config.getString("bucket-key-prefix");
        bucketKeyPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        creds = akka.stream.alpakka.s3.auth.AWSCredentials$.MODULE$.apply(
                s3Config.getString("key"), s3Config.getString("secret"));
        region = s3Config.getString("region");
        Config refConfig = config.getConfig("reference");
        BufferType bufferType;
        if(refConfig.getString("akka.stream.alpakka.s3.buffer").equals("disk")) {
            bufferType = DiskBufferType.getInstance();            
        } else {
            bufferType = MemoryBufferType.getInstance();
        }
        boolean pathStyleAccess = refConfig.getBoolean("akka.stream.alpakka.s3.path-style-access");
        settings = new S3Settings(bufferType, bucket, scala.Option.empty(), creds, bucket, pathStyleAccess);
    }
    
    /**
     * Stores the given buffered events into S3, under a key that includes the tag and the offset of the first event.
     * @param tag Persistence tag that the events were for
     */
    public CompletionStage<Done> store(String tag, Seq<EventEnvelope2> events) {
        String key = tag + SEPARATOR + FMT.format(Instant.ofEpochMilli(UUIDs.unixTimestamp(TimeBasedUUID.class.cast(events.get(0).offset()).value())));
        return Source.from(events)
              .map(e -> {
                  ByteStringBuilder b = new ByteStringBuilder();
                  serializer.toProtobuf(e).writeDelimitedTo(b.asOutputStream());
                  return b.result();
              })
              .runWith(upload(key), materializer)
              .thenApply(result -> {
                  log.info("Uploaded to {} with etag {}", result.key(), result.etag());
                  return Done.getInstance();
              });
    }
    
    /**
     * Returns the instant of the first event saved under the given entry, by parsing its key name.
     */
    public static Instant getStartInstant(S3Entry entry) {
        int i = entry.getKey().lastIndexOf(SEPARATOR);
        if (i == -1) throw new IllegalArgumentException("Expected " + entry.getKey() + " to contain " + SEPARATOR);
        return FMT.parse(entry.getKey().substring(i + SEPARATOR.length()), Instant::from);
    }
    
    /**
     * Loads the last known written offset from S3, or returns 0 if not found
     */
    public CompletionStage<Long> loadOffset() {
        return download("_lastOffset").thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(0l);
            } else {
                return opt.get().runFold(ByteString.empty(), ByteString::concat, materializer)
                                .thenApply(bs -> Long.parseLong(bs.utf8String()));
            }
        });
    }
    
    /**
     * Writes the last known offset to S3
     */
    public CompletionStage<Done> saveOffset(long offset) {
        return Source.single(ByteString.fromString(String.valueOf(offset)))
                     .runWith(upload("_lastOffset"), materializer)
                     .thenApply(result -> Done.getInstance());
    }
    
    private Sink<ByteString, CompletionStage<CompleteMultipartUploadResult>> upload(String key) {
        S3Location s3Location = toLocation(key);
        log.debug("Uploading to {}", s3Location);
        return s3stream()
            .multipartUpload(s3Location, ContentTypes.application$divoctet$minusstream(),
                    S3Headers.empty(), 5242880, 1)
            .asJava()
            .mapMaterializedValue(f -> toJava(f));
    }

    private S3Location toLocation(String key) {
        return S3Location.apply(bucket, bucketKeyPrefix + key);
    }
    
    private S3Stream s3stream() {
        return new S3Stream(settings, system, materializer);
    }
    
    private CompletionStage<Option<Source<ByteString,NotUsed>>> download(String key) {
        S3Location s3Location = toLocation(key);
        log.debug("Downloading from {}", s3Location);
        SigningKey signingKey = SigningKey.apply(creds, CredentialScope.apply(LocalDate.now(), region, "s3"), SigningKey.apply$default$3());
        
        Future<akka.http.scaladsl.model.HttpRequest> request = Signer.signedRequest(
            (akka.http.scaladsl.model.HttpRequest)
            HttpRequest.create()
            .withMethod(HttpMethods.GET)
            .withUri(new JavaUri(requestUri(s3Location)))
            .addHeader(requestHost(s3Location))
        , signingKey, Signer.signedRequest$default$3(), materializer);
        
        return toJava(request)
            .thenCompose(rq -> http.singleRequest(rq, materializer))
            .thenCompose(rs -> {
                return getOptionBody(rs);
        });
    }

    private CompletionStage<Option<Source<ByteString, NotUsed>>> getOptionBody(HttpResponse rs) {
        log.debug("Got a response: {}", rs);
        if (rs.status().equals(StatusCodes.NOT_FOUND)) {
            return CompletableFuture.completedFuture(Option.none());
        } else if (rs.status().isFailure()) {
            return
                Unmarshaller.entityToString().unmarshal(rs.entity(), system.dispatcher(), materializer)
                .thenApply(msg -> {throw new IllegalStateException ("S3 request failed: " + msg);});
        } else {
            return CompletableFuture.completedFuture(Option.some(rs.entity().getDataBytes().mapMaterializedValue(o -> NotUsed.getInstance())));
        }
    }

    static class S3ListResponse {
        private final Option<String> nextContinuationToken;
        private final Seq<S3Entry> entries;
        
        public S3ListResponse(Option<String> nextContinuationToken, Seq<S3Entry> entries) {
            this.nextContinuationToken = nextContinuationToken;
            this.entries = entries;
        }
        
        public Seq<S3Entry> getEntries() {
            return entries;
        }
        
        public Option<String> getNextContinuationToken() {
            return nextContinuationToken;
        }

        private static final Namespace NS = ns("http://s3.amazonaws.com/doc/2006-03-01/");
        static final ReadProtocol<XMLEvent,S3ListResponse> proto =
            tag(qname(NS, "ListBucketResult"),
                option(
                    tag(qname(NS, "ContinuationToken"), body)
                ),
                vector(
                    tag(qname(NS, "Contents"),
                        tag(qname(NS, "Key"), body),
                        tag(qname(NS, "LastModified"), body.as(StringMarshallable.INSTANT)),
                        tag(qname(NS, "Size"), body.as(StringMarshallable.LONG)),
                        S3Entry::new
                    )
                ),
                S3ListResponse::new
            );
    }
        
    public Source<S3Entry, NotUsed> list(String keyPrefix) {
        return Source.<String,Seq<S3Entry>>unfoldAsync("", continuationToken -> {
            if (continuationToken.equals("done")) {
                return CompletableFuture.completedFuture(Optional.empty());
            } else {
                return list(keyPrefix, continuationToken.isEmpty() ? none() : some(continuationToken))
                    .thenApply(response -> {
                        if (response.nextContinuationToken.isDefined()) {
                            return Optional.of(Pair.create(response.nextContinuationToken.get(), response.entries));
                        } else {
                            return Optional.of(Pair.create("done", response.entries));
                        }
                    });
            }
        }).flatMapConcat(seq -> Source.from(seq));
    }
    
    private CompletionStage<S3ListResponse> list(String keyPrefix, Option<String> continuationToken) {
        S3Location s3Location = toLocation(keyPrefix);
        log.debug("Listing {}", s3Location);
        SigningKey signingKey = SigningKey.apply(creds, CredentialScope.apply(LocalDate.now(), region, "s3"), SigningKey.apply$default$3());
        
        StringBuilder queryParams = new StringBuilder();
        // TODO max return size configurable, default to 1000 is fine
        queryParams.append("list-type=2&prefix=").append(bucketKeyPrefix).append(keyPrefix);
        continuationToken.map(s -> queryParams.append("&continuation-token").append(s));

        Future<akka.http.scaladsl.model.HttpRequest> request = Signer.signedRequest(
            (akka.http.scaladsl.model.HttpRequest)
            HttpRequest.create()
            .withMethod(HttpMethods.GET)
            .withUri(new JavaUri(Uri.apply("http://" + requestHost(s3Location).name() + "/")
                    .withQuery(Uri.Query$.MODULE$.apply(queryParams.toString()))))
            .addHeader(requestHost(s3Location))
        , signingKey, Signer.signedRequest$default$3(), materializer);
        
        return Source.fromCompletionStage(toJava(request)
            .thenCompose(rq -> http.singleRequest(rq, materializer))
            .thenCompose(this::getOptionBody))
            .flatMapConcat(opt -> {
                if (opt.isDefined()) {
                    log.debug("Parsing S3 bucket list starting.");
                    return opt.get();
                } else {
                    log.warn("Huh, empty body for S3 list?");
                    return Source.<ByteString>empty();
                }
            })
            .log("list-" + keyPrefix, bs -> bs.utf8String())
            .via(AaltoReader.instance)
            .via(ProtocolReader.of(S3ListResponse.proto))
            .runWith(Sink.head(), materializer);
            
            
    }

    private Host requestHost(S3Location s3Location) {
        return Host.create(String.format("%s.s3.amazonaws.com", s3Location.bucket()));
    }

    private Uri requestUri(S3Location s3Location) {
        return Uri.apply("/" + s3Location.key()).withHost(requestHost(s3Location).name()).withScheme("https");
    }
    
    /**
     * Reads the stream of events written to S3 using {@link #store(String, List)} before.
     */
    public Source<com.tradeshift.reaktive.protobuf.Query.EventEnvelope, NotUsed> loadEvents(String key) {
        return Source.fromCompletionStage(
            download(key).thenApply(opt -> opt.getOrElse(Source.empty()))
        ).flatMapConcat(src -> src)
        .via(DelimitedProtobufFraming.instance)
        .map(bs -> com.tradeshift.reaktive.protobuf.Query.EventEnvelope.parseFrom(bs.iterator().asInputStream()));
    }
}
