package com.tradeshift.reaktive.ssl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Scanner;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

@RunWith(CuppaRunner.class)
public class SSLFactorySpec {{
    describe("SSLFactory.createKeystore", () -> {
        it("should read a keyfile and multi-ca certificate chain correctly", () -> {
            
            char[] password = "foo".toCharArray();
            String privatekey = getResource("ca.key");
            String certificateChain = getResource("ca.crt") + "\n" + getResource("intermediate1.crt");
            KeyStore store = SSLFactory.createKeystore(password, privatekey, certificateChain);
            
            assertThat(store).isNotNull();
            Certificate ca = store.getCertificate("CN=localhost,O=Internet Widgits Pty Ltd,ST=Some-State,C=AU");
            assertThat(ca.getType()).isEqualTo("X.509");
            Certificate intermediate = store.getCertificate("O=Internet Widgits Pty Ltd,ST=Some-State,C=AU");
            assertThat(intermediate.getType()).isEqualTo("X.509");
            Key key = store.getKey("key", password);
            assertThat(key.getFormat()).isEqualTo("PKCS#8");
        });
    });
}

private String getResource(String string) {
    try (Scanner s = new Scanner(getClass().getClassLoader().getResourceAsStream(string))) {
        return s.useDelimiter("\\A").next();
    }
}}
