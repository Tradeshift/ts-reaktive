package com.tradeshift.reaktive.backup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.utils.UUIDs;
import com.tradeshift.reaktive.protobuf.DelimitedProtobufFraming;
import com.tradeshift.reaktive.protobuf.EventEnvelopeSerializer;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.pf.PFBuilder;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.TimeBasedUUID;
import akka.stream.Materializer;
import akka.stream.alpakka.s3.javadsl.ListBucketResultContents;
import akka.stream.alpakka.s3.javadsl.MultipartUploadResult;
import akka.stream.alpakka.s3.javadsl.S3Client;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import io.vavr.collection.Seq;

/**
 * Small wrapper atop Alpakka's S3 support
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
    private final Materializer materializer;
    private final EventEnvelopeSerializer serializer;
	private final S3Client client;

    public S3(ActorSystem system, Materializer materializer, EventEnvelopeSerializer serializer, String bucket, String prefix) {
        this.materializer = materializer;
        this.serializer = serializer;
        this.bucket = bucket;
        this.bucketKeyPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        this.client = S3Client.create(system, materializer);
    }
    
    /**
     * Stores the given buffered events into S3, under a key that includes the tag and the offset of the first event.
     * @param tag Persistence tag that the events were for
     */
    public CompletionStage<Done> store(String tag, Seq<EventEnvelope> events) {
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
    public static Instant getStartInstant(ListBucketResultContents entry) {
        int i = entry.key().lastIndexOf(SEPARATOR);
        if (i == -1) throw new IllegalArgumentException("Expected " + entry.key() + " to contain " + SEPARATOR);
        return FMT.parse(entry.key().substring(i + SEPARATOR.length()), Instant::from);
    }
    
    /**
     * Loads the last known written offset from S3, or returns 0 if not found
     */
    public CompletionStage<Long> loadOffset() {
        return download("_lastOffset")
    		.reduce((bs1, bs2) -> bs1.concat(bs2))
    		.map(bs -> Long.parseLong(bs.utf8String()))
    		.recoverWith(new PFBuilder<Throwable, Source<Long,NotUsed>>()
    			.matchAny(x -> Source.single(0L)) // not found -> start at 0
				.build()
			)
    		.runWith(Sink.head(), materializer);
    }
    
    /**
     * Writes the last known offset to S3
     */
    public CompletionStage<Done> saveOffset(long offset) {
        return Source.single(ByteString.fromString(String.valueOf(offset)))
                     .runWith(upload("_lastOffset"), materializer)
                     .thenApply(result -> Done.getInstance());
    }
    
    private Sink<ByteString, CompletionStage<MultipartUploadResult>> upload(String key) {
    	return client.multipartUpload(bucket, bucketKeyPrefix + key);
    }

    
    private Source<ByteString, NotUsed> download(String key) {
    	return client.download(bucket, bucketKeyPrefix + key);
    }

    public Source<ListBucketResultContents, NotUsed> list(String keyPrefix) {
    	return client.listBucket(bucket, scala.Option.apply(bucketKeyPrefix + keyPrefix));
    }
    
    /**
     * Reads the stream of events written to S3 using {@link #store(String, Seq)} before.
     */
    public Source<com.tradeshift.reaktive.protobuf.Query.EventEnvelope, NotUsed> loadEvents(String key) {
        return download(key)
		.recoverWith(new PFBuilder<Throwable, Source<ByteString,NotUsed>>()
			.matchAny(x -> Source.empty()) // not found -> no data
			.build()
		)
        .via(DelimitedProtobufFraming.instance)
        .map(bs -> com.tradeshift.reaktive.protobuf.Query.EventEnvelope.parseFrom(bs.iterator().asInputStream()));
    }
}
