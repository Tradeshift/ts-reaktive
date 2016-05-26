package com.tradeshift.reaktive.akka.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/**
 * Factory methods to create HTTPS and JSE configuration objects
 */
public class HttpsContextFactory {
    /**
     * Creates an in-memory KeyStore by reading a certificate chain and private/public keypair from two base64-format PEM files.
     * 
     * @param password Password to encrypt the keystore with
     * @param privatekey PEM-encoded private key
     * @param certificateChain PEM-encoded certificate(s), concatenated
     */
    public static KeyStore createKeystore(char[] password, String privatekey, String certificateChain) throws KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore ks = KeyStore.getInstance("JKS");

        try ( StringReader keyReader = new StringReader(privatekey);
              InputStream caStream = new ByteArrayInputStream(certificateChain.getBytes());
              PEMParser pemReader = new PEMParser(keyReader);
            ) {
            
            // Initialize empty keystore
            ks.load(null, password);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ks.store(buffer, password);
            ks.load(new ByteArrayInputStream(buffer.toByteArray()), password);

            // store certificates
            Certificate[] chain = CertificateFactory.getInstance("X509").generateCertificates(caStream).toArray(new Certificate[0]);
            for (Certificate cert: chain) {
                String caAlias = ((X509Certificate) cert).getSubjectX500Principal().getName();
                ks.setCertificateEntry(caAlias, cert);
                
            }

            // store the private key
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            KeyPair keyPair = converter.getKeyPair((PEMKeyPair) pemReader.readObject());
            ks.setKeyEntry("key", keyPair.getPrivate(), password, chain);
        } catch (IOException e) {
            // Shouldn't occur, since we're only reading from strings
            throw new RuntimeException(e);
        }
        return ks;
    }
    
    /**
     * Create an SSL context based on a KeyStore
     * 
     * @param ks A keystore with a private key and certificate chain.
     * @param password the password for the keystore.
     */
    public static SSLContext createSSLContext(KeyStore ks, char[] password) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException, FileNotFoundException, UnrecoverableKeyException {
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), SecureRandom.getInstance("SHA1PRNG"));
        return sslContext;
    }
}
