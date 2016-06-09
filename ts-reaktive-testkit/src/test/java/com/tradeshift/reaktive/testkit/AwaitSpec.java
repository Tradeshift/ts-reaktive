package com.tradeshift.reaktive.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

@RunWith(CuppaRunner.class)
public class AwaitSpec {{
    describe("Await.eventuallyDo", () -> {
        it("should immediately throw any non-assertion exception", () -> {
            AtomicInteger count = new AtomicInteger();
            
            assertThatThrownBy(() -> 
                Await.eventuallyDo(() -> {
                    count.incrementAndGet();
                    throw new RuntimeException("simulated failure");
                })
            ).hasMessageContaining("simulated failure");
            
            assertThat(count.get()).isEqualTo(1);
        });
        
        it("should retry on assertion errors", () -> {
            AtomicInteger count = new AtomicInteger();
            
            Await.eventuallyDo(() -> {
                int i = count.incrementAndGet();
                assertThat(i).isGreaterThan(2);
            });
            
            assertThat(count.get()).isEqualTo(3);            
        });
    });
    
    describe("Await.eventually", () -> {
        it("should return the value of the inner lambda if successful", () -> {
            assertThat(
                Await.eventually(() -> 5)
            ).isEqualTo(5);
        });
    });
    
    describe("Await.within", () -> {
        it("should abort when the given timeout has passed", () -> {
            
            assertThatThrownBy(() -> 
                Await.within(1, TimeUnit.MILLISECONDS).eventuallyDo(() -> {
                    Thread.sleep(10);
                    assertThat(false).isEqualTo(true);
                }))
            .hasMessageContaining("Timed out")
            .isInstanceOf(AssertionError.class);
        });
    });
}}
