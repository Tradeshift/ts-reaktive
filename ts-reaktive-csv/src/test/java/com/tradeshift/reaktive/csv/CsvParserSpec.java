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
import javaslang.Tuple2;
import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.collection.Vector;

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
            Map<String,Seq<String>> cases = HashMap.of(
                // Old Ruby 1.8 CSV library tests.
                "\t", Vector.of("\t"),
                "foo,\"\"\"\"\"\",baz", Vector.of("foo", "\"\"", "baz"),
                "foo,\"\"\"bar\"\"\",baz", Vector.of("foo", "\"bar\"", "baz"),
                "\"\"\"\n\",\"\"\"\n\"", Vector.of("\"\n", "\"\n"),
                "foo,\"\r\n\",baz", Vector.of("foo", "\r\n", "baz"),
                "\"\"", Vector.of(""),
                "foo,\"\"\"\",baz", Vector.of("foo", "\"", "baz"),
                "foo,\"\r.\n\",baz", Vector.of("foo", "\r.\n", "baz"),
                "foo,\"\r\",baz", Vector.of("foo", "\r", "baz"),
                "foo,\"\",baz", Vector.of("foo", "", "baz"),
                "\",\"", Vector.of(","),
                "foo", Vector.of("foo"),
                ",,", Vector.of("", "", ""),
                ",", Vector.of("", ""),
                "foo,\"\n\",baz", Vector.of("foo", "\n", "baz"),
                "foo,,baz", Vector.of("foo", "", "baz"),
                "\"\"\"\r\",\"\"\"\r\"", Vector.of("\"\r", "\"\r"),
                "\",\",\",\"", Vector.of(",", ","),
                "foo,bar,", Vector.of("foo", "bar", ""),
                ",foo,bar", Vector.of("", "foo", "bar"),
                "foo,bar", Vector.of("foo", "bar"),
                ";", Vector.of(";"),
                "\t,\t", Vector.of("\t", "\t"),
                "foo,\"\r\n\r\",baz", Vector.of("foo", "\r\n\r", "baz"),
                "foo,\"\r\n\n\",baz", Vector.of("foo", "\r\n\n", "baz"),
                "foo,\"foo,bar\",baz", Vector.of("foo", "foo,bar", "baz"),
                ";,;", Vector.of(";", ";"),
                
                // test_area_edge_cases
                "a,b", Vector.of("a", "b"),
                "a,\"\"\"b\"\"\"", Vector.of("a", "\"b\""),
                "a,\"\"\"b\"", Vector.of("a", "\"b"),
                "a,\"b\"\"\"", Vector.of("a", "b\""),
                "a,\"\nb\"\"\"", Vector.of("a", "\nb\""),
                "a,\"\"\"\nb\"", Vector.of("a", "\"\nb"),
                "a,\"\"\"\nb\n\"\"\"", Vector.of("a", "\"\nb\n\""),
                "a,,,", Vector.of("a", "", "", ""),
                "\"\",\"\"", Vector.of("", ""),
                "\"\"\"\"", Vector.of("\""),
                "\"\"\"\",\"\"", Vector.of("\"",""),
                ",\"\"", Vector.of("",""),
                ",\"\r\"", Vector.of("","\r"),
                "\"\r\n,\"", Vector.of("\r\n,"),
                "\"\r\n,\",", Vector.of("\r\n,", ""),
                
                // test_non_regex_edge_cases
                "foo,\"foo,bar,baz,foo\",\"foo\"", Vector.of("foo", "foo,bar,baz,foo", "foo")
              );
            
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
