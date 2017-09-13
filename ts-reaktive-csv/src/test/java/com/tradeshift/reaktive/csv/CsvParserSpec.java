package com.tradeshift.reaktive.csv;

import static com.tradeshift.reaktive.csv.CsvEvent.endRecord;
import static com.tradeshift.reaktive.csv.CsvEvent.endValue;
import static com.tradeshift.reaktive.csv.CsvEvent.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class CsvParserSpec extends SharedActorSystemSpec {{
    describe("CsvParser", () -> {
        it("should parse a complete unquoted CSV passed in a single string", () -> {
            List<CsvEvent> result = Source.single("hello;world\nwhat's;up")
                .via(new CsvParser(CsvSettings.RFC4180))
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactly(
                text("hello"),
                endValue(),
                text("world"),
                endValue(),
                endRecord(),
                text("what's"),
                endValue(),
                text("up"),
                endValue(),
                endRecord()
            );
        });
        
        it("should parse a complete quoted CSV passed in a single string", () -> {
            List<CsvEvent> result = Source.single("\"hello\";\"world\"\n\"what\"\"s\";\"up\"")
                .via(new CsvParser(CsvSettings.RFC4180))
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactly(
                text("hello"),
                endValue(),
                text("world"),
                endValue(),
                endRecord(),
                text("what\"s"),
                endValue(),
                text("up"),
                endValue(),
                endRecord()
            );
        });
        
        it("should process unquoted CSV that arrives in buffers of 1", () -> {
            List<CsvEvent> result = Source.fromIterator(() -> "hello;world\nwhat's;up".chars().iterator())
                .map(ch -> "" + ((char)ch.intValue()))
                .via(new CsvParser(CsvSettings.RFC4180))
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactly(
                text("h"),
                text("e"),
                text("l"),
                text("l"),
                text("o"),
                endValue(),
                text("w"),
                text("o"),
                text("r"),
                text("l"),
                text("d"),
                endValue(),
                endRecord(),
                text("w"),
                text("h"),
                text("a"),
                text("t"),
                text("'"),
                text("s"),
                endValue(),
                text("u"),
                text("p"),
                endValue(),
                endRecord()
            );
        });
        
        it("should process quoted CSV that arrives in buffers of 1", () -> {
            List<CsvEvent> result = Source.fromIterator(() -> "\"hello\";\"world\"\n\"what\"\"s\";\"up\"".chars().iterator())
                .map(ch -> "" + ((char)ch.intValue()))
                .via(new CsvParser(CsvSettings.RFC4180))
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactly(
                text("h"),
                text("e"),
                text("l"),
                text("l"),
                text("o"),
                endValue(),
                text("w"),
                text("o"),
                text("r"),
                text("l"),
                text("d"),
                endValue(),
                endRecord(),
                text("w"),
                text("h"),
                text("a"),
                text("t"),
                text("\""),
                text("s"),
                endValue(),
                text("u"),
                text("p"),
                endValue(),
                endRecord()
            );
        });
        
        it("should parse a variety of edge cases successfully", () -> {
            CsvSettings COMMA_SEPARATOR = new CsvSettings('"', "\"\"", ',');
            
            // from https://github.com/ruby/ruby/blob/trunk/test/csv/test_csv_parsing.rb
            Map<String,Seq<String>> cases = HashMap.<String,Seq<String>>empty()
                // Old Ruby 1.8 CSV library tests.
                .put("\t", Vector.of("\t"))
                .put("foo,\"\"\"\"\"\",baz", Vector.of("foo", "\"\"", "baz"))
                .put("foo,\"\"\"bar\"\"\",baz", Vector.of("foo", "\"bar\"", "baz"))
                .put("\"\"\"\n\",\"\"\"\n\"", Vector.of("\"\n", "\"\n"))
                .put("foo,\"\r\n\",baz", Vector.of("foo", "\r\n", "baz"))
                .put("\"\"", Vector.of(""))
                .put("foo,\"\"\"\",baz", Vector.of("foo", "\"", "baz"))
                .put("foo,\"\r.\n\",baz", Vector.of("foo", "\r.\n", "baz"))
                .put("foo,\"\r\",baz", Vector.of("foo", "\r", "baz"))
                .put("foo,\"\",baz", Vector.of("foo", "", "baz"))
                .put("\",\"", Vector.of(","))
                .put("foo", Vector.of("foo"))
                .put(",,", Vector.of("", "", ""))
                .put(",", Vector.of("", ""))
                .put("foo,\"\n\",baz", Vector.of("foo", "\n", "baz"))
                .put("foo,,baz", Vector.of("foo", "", "baz"))
                .put("\"\"\"\r\",\"\"\"\r\"", Vector.of("\"\r", "\"\r"))
                .put("\",\",\",\"", Vector.of(",", ","))
                .put("foo,bar,", Vector.of("foo", "bar", ""))
                .put(",foo,bar", Vector.of("", "foo", "bar"))
                .put("foo,bar", Vector.of("foo", "bar"))
                .put(";", Vector.of(";"))
                .put("\t,\t", Vector.of("\t", "\t"))
                .put("foo,\"\r\n\r\",baz", Vector.of("foo", "\r\n\r", "baz"))
                .put("foo,\"\r\n\n\",baz", Vector.of("foo", "\r\n\n", "baz"))
                .put("foo,\"foo,bar\",baz", Vector.of("foo", "foo,bar", "baz"))
                .put(";,;", Vector.of(";", ";"))
                
                // test_area_edge_cases
                .put("a,b", Vector.of("a", "b"))
                .put("a,\"\"\"b\"\"\"", Vector.of("a", "\"b\""))
                .put("a,\"\"\"b\"", Vector.of("a", "\"b"))
                .put("a,\"b\"\"\"", Vector.of("a", "b\""))
                .put("a,\"\nb\"\"\"", Vector.of("a", "\nb\""))
                .put("a,\"\"\"\nb\"", Vector.of("a", "\"\nb"))
                .put("a,\"\"\"\nb\n\"\"\"", Vector.of("a", "\"\nb\n\""))
                .put("a,,,", Vector.of("a", "", "", ""))
                .put("\"\",\"\"", Vector.of("", ""))
                .put("\"\"\"\"", Vector.of("\""))
                .put("\"\"\"\",\"\"", Vector.of("\"",""))
                .put(",\"\"", Vector.of("",""))
                .put(",\"\r\"", Vector.of("","\r"))
                .put("\"\r\n,\"", Vector.of("\r\n,"))
                .put("\"\r\n,\",", Vector.of("\r\n,", ""))
                
                // test_non_regex_edge_cases
                .put("foo,\"foo,bar,baz,foo\",\"foo\"", Vector.of("foo", "foo,bar,baz,foo", "foo"));
            
            for (Tuple2<String,Seq<String>> c: cases) {
                Seq<CsvEvent> expected = c._2.flatMap(s ->
                    (s.isEmpty()) ? Vector.of(endValue()) : Vector.of(text(s), endValue())
                ).append(endRecord());
                
                // test when passed as a single token
                List<CsvEvent> result = Source.single(c._1)
                    .via(new CsvParser(COMMA_SEPARATOR))
                    .runWith(Sink.seq(), materializer)
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
                assertThat(result).containsExactlyElementsOf(expected);
                
                // test when passed as individual chars
                result = Source.fromIterator(() -> c._1.chars().iterator())
                    .map(ch -> "" + ((char)ch.intValue()))
                    .via(new CsvParser(COMMA_SEPARATOR))
                    .via(CsvTextAggregator.create())
                    .runWith(Sink.seq(), materializer)
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
                assertThat(result).containsExactlyElementsOf(expected);
            };
        });
    });
}}
