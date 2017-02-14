package com.tradeshift.reaktive.csv.marshal;

import static com.tradeshift.reaktive.csv.marshal.CsvProtocol.column;
import static com.tradeshift.reaktive.csv.marshal.CsvProtocol.csv;
import static com.tradeshift.reaktive.marshal.Protocol.option;
import static javaslang.control.Option.none;
import static javaslang.control.Option.some;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.csv.CsvEvent;
import com.tradeshift.reaktive.csv.CsvParser;
import com.tradeshift.reaktive.csv.CsvSettings;
import com.tradeshift.reaktive.csv.CsvWriter;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.StringMarshallable;
import com.tradeshift.reaktive.marshal.stream.ProtocolReader;
import com.tradeshift.reaktive.marshal.stream.ProtocolWriter;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class CsvProtocolSpec extends SharedActorSystemSpec {
    {
        describe("a protocol mapping several columns from a CSV stream", () -> {
            Protocol<CsvEvent,DTO> proto = csv(
                column("title"),
                column("age").as(StringMarshallable.INTEGER),
                DTO::new,
                DTO::getText,
                DTO::getNumber
            );

            it("should read several items correctly", () -> {
                String input = "title;age\nhello;15\nworld;42";
                assertThat(parse(proto, input)).containsExactly(new DTO("hello", 15), new DTO("world", 42));
            });

            it("should report no values if a required column is missing", () -> {
                String input = "title;foobar\nhello;15\nworld;42";
                assertThat(parse(proto, input)).isEmpty();
            });

            it("should render several values to csv correctly", () -> {
                String output = render(proto, new DTO("hello", 15), new DTO("world", 42));
                assertThat(output).isEqualTo(
                      "\"title\";\"age\"\n"
                    + "\"hello\";\"15\"\n"
                    + "\"world\";\"42\"\n");
            });

            it("not even render a header row if there are no values in the stream", () -> {
                String output = render(proto);
                assertThat(output).isEqualTo("");
            });
        });

        describe("a protocol marking a certain column as optional", () -> {
            Protocol<CsvEvent,OptionDTO> proto = csv(
                column("title"),
                option(
                    column("age").as(StringMarshallable.INTEGER)
                ),
                option(
                    column("timestamp").as(StringMarshallable.LONG)
                ),
                OptionDTO::new,
                OptionDTO::getText,
                OptionDTO::getIntOption,
                OptionDTO::getLongOption
            );

            it("should report none() for a CSV where a whole named column is missing", () -> {
                String input = "title;description\nhello;foo\nworld;bar";
                assertThat(parse(proto, input)).containsExactly(new OptionDTO("hello", none(), none()), new OptionDTO("world", none(), none()));
            });

            it("should report none() for a CSV where one line has fewer columns", () -> {
                String input = "title;age\nhello\nworld;42";
                assertThat(parse(proto, input)).containsExactly(new OptionDTO("hello", none(), none()), new OptionDTO("world", some(42), none()));
            });

            it("should report none() for a CSV where one line has the column as empty", () -> {
                String input = "title;age\nhello;;\nworld;42";
                assertThat(parse(proto, input)).containsExactly(new OptionDTO("hello", none(), none()), new OptionDTO("world", some(42), none()));
            });

            it("should render empty columns for values having none()", () -> {
                String output = render(proto, new OptionDTO("hello", none(), some(123l)), new OptionDTO("world", some(42), none()));
                assertThat(output).isEqualTo(
                      "\"title\";\"age\";\"timestamp\"\n"
                    + "\"hello\";;\"123\"\n"
                    + "\"world\";\"42\";\n");
            });
        });

        describe("a protocol marking the first column as optional", () -> {
            Protocol<CsvEvent,OptionDTO> proto = csv(
                option(
                    column("age").as(StringMarshallable.INTEGER)
                ),
                column("title"),
                option(
                    column("timestamp").as(StringMarshallable.LONG)
                ),
                (i,s,l) -> new OptionDTO(s, i, l),
                OptionDTO::getIntOption,
                OptionDTO::getText,
                OptionDTO::getLongOption
            );

            it("should render empty columns for values having none()", () -> {
                String output = render(proto, new OptionDTO("hello", none(), some(123l)), new OptionDTO("world", some(42), none()));
                assertThat(output).isEqualTo(
                      "\"age\";\"title\";\"timestamp\"\n"
                    + ";\"hello\";\"123\"\n"
                    + "\"42\";\"world\";\n");
            });
        });
    }

    private <T> List<T> parse(Protocol<CsvEvent, T> proto, String input)
        throws InterruptedException, ExecutionException, TimeoutException {
        List<T> output = Source.single(input)
            .via(new CsvParser(CsvSettings.RFC4180))
            .via(ProtocolReader.of(proto))
            .runWith(Sink.seq(), materializer)
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS);
        return output;
    }

    private <T> String render(Protocol<CsvEvent, T> proto, @SuppressWarnings("unchecked") T... elements)
        throws InterruptedException, ExecutionException, TimeoutException {
        return Source.from(Arrays.asList(elements))
            .via(ProtocolWriter.of(proto))
            .via(new CsvWriter(CsvSettings.RFC4180))
            .runFold("", (a,b) -> a + b, materializer)
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS);
    }
}
