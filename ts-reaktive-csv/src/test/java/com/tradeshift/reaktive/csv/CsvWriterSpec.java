package com.tradeshift.reaktive.csv;

import static com.tradeshift.reaktive.csv.CsvEvent.endRecord;
import static com.tradeshift.reaktive.csv.CsvEvent.endValue;
import static com.tradeshift.reaktive.csv.CsvEvent.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class CsvWriterSpec extends SharedActorSystemSpec {{
    describe("CsvWriter", () -> {
        it("Should render valid events into CSV", () -> {
            String result = Source.from(Arrays.asList(
                text("name"), endValue(), text("age"), endValue(), endRecord(),
                text("hello"), endValue(), text("42"), endValue(), endRecord(),
                text("\"me\""), endValue(), endValue(), endRecord(),
                text(""), text(""), endValue(), endRecord(),
                text(""), text(""), endValue(), text("5"), endValue(), endRecord()
            ))
            .via(new CsvWriter(CsvSettings.RFC4180))
            .runFold("", (a,b) -> a + b, materializer)
            .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).isEqualTo(
                  "\"name\";\"age\"\n"   // "name";"age"
                + "\"hello\";\"42\"\n"   // "hello";"42"
                + "\"\"\"me\"\"\";\n"    // """me""";
                + "\n"                   //
                + ";\"5\"\n"             // ;"5"
            );
        });
        
        it("Should end a stray field if endRecord() erroneously appears before endValue()", () -> {
            String result = Source.from(Arrays.asList(
                text("name"), endValue(), text("age"), endRecord()
            ))
            .via(new CsvWriter(CsvSettings.RFC4180))
            .runFold("", (a,b) -> a + b, materializer)
            .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).isEqualTo(
                  "\"name\";\"age\"\n"   // "name";"age"
            );
        });
    });
}}
