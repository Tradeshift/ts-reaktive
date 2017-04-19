package com.tradeshift.reaktive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.UUID;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

@RunWith(CuppaRunner.class)
public class NameBasedUUIDSpec {{

    describe("NameBasedUUID", () -> {
        final UUID namespace = UUID.fromString("49da330a-35f6-40f2-8e33-570e79859030");
        final UUID actorId = UUID.fromString("e330948c-efd7-4cae-b6cc-1c4118d47649");
        final String clientToken = "7f1b889b-ebc2-409b-89db-f7ec7932ddef";

        it("should return one UUID for the same input parameters", () -> {
            assertThat(
                NameBasedUUID.create(namespace, actorId + ":" + clientToken)
            ).isEqualTo(
                NameBasedUUID.create(namespace, actorId + ":" + clientToken)
            );
        });

        it("should return different UUIDs for different input parameters", () -> {
            assertThat(
                NameBasedUUID.create(namespace, actorId + ":" + "dce56e77-ec6c-414d-9e2a-3a078981f1d9")
            ).isNotEqualTo(
                NameBasedUUID.create(namespace, actorId + ":" + "935d84c3-01d8-4ad7-ad52-3c4d19ee7e80")
            );

            assertThat(
                NameBasedUUID.create(namespace, "dce56e77-ec6c-414d-9e2a-3a078981f1d9" + ":" + clientToken)
            ).isNotEqualTo(
                NameBasedUUID.create(namespace, "935d84c3-01d8-4ad7-ad52-3c4d19ee7e80" + ":" + clientToken)
            );

            assertThat(
                NameBasedUUID.create(UUID.randomUUID(), actorId + ":" + clientToken)
            ).isNotEqualTo(
                NameBasedUUID.create(UUID.randomUUID(), actorId + ":" + clientToken)
            );
        });
    });
    
}}
