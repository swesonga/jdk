/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8296343
 * @summary CPVE thrown on missing content-length in OCSP response
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm OCSPNoContentLength
 */

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.*;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.TimeUnit;


import jdk.test.lib.security.SimpleOCSPServer;
import jdk.test.lib.security.CertificateBuilder;

public class OCSPNoContentLength {

    static String passwd = "passphrase";
    static String ROOT_ALIAS = "root";
    static String EE_ALIAS = "endentity";

    // Enable debugging for additional output
    static final boolean debug = true;

    // PKI components we will need for this test
    static X509Certificate rootCert;        // The root CA certificate
    static X509Certificate eeCert;          // The end entity certificate
    static KeyStore rootKeystore;           // Root CA Keystore
    static KeyStore eeKeystore;             // End Entity Keystore
    static KeyStore trustStore;             // SSL Client trust store
    static SimpleOCSPServer rootOcsp;       // Root CA OCSP Responder
    static int rootOcspPort;                // Port number for root OCSP

    public static void main(String[] args) throws Exception {

        try {
            createPKI();

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath path = cf.generateCertPath(List.of(eeCert));
            log("%s", path);

            TrustAnchor anchor = new TrustAnchor(rootCert, null);
            log("%s", anchor);
            Set<TrustAnchor> anchors = Set.of(anchor);

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXParameters params = new PKIXParameters(anchors);
            PKIXRevocationChecker prc =
                (PKIXRevocationChecker)validator.getRevocationChecker();
            params.addCertPathChecker(prc);

            validator.validate(path, params);
        } finally {
            rootOcsp.stop();
        }
    }


    /**
     * Creates the PKI components necessary for this test, including
     * Root CA, Intermediate CA and SSL server certificates, the keystores
     * for each entity, a client trust store, and starts the OCSP responders.
     */
    private static void createPKI() throws Exception {
        CertificateBuilder cbld = new CertificateBuilder();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyStore.Builder keyStoreBuilder =
                KeyStore.Builder.newInstance("PKCS12", null,
                        new KeyStore.PasswordProtection(passwd.toCharArray()));

        // Generate Root and EE keys
        KeyPair rootCaKP = keyGen.genKeyPair();
        log("Generated Root CA KeyPair");
        KeyPair eeKP = keyGen.genKeyPair();
        log("Generated End Entity KeyPair");

        // Set up the Root CA Cert
        cbld.setSubjectName("CN=Root CA Cert, O=SomeCompany");
        cbld.setPublicKey(rootCaKP.getPublic());
        cbld.setSerialNumber(new BigInteger("1"));
        // Make a 3 year validity starting from 60 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        long end = start + TimeUnit.DAYS.toMillis(1085);
        cbld.setValidity(new Date(start), new Date(end));
        addCommonExts(cbld, rootCaKP.getPublic(), rootCaKP.getPublic());
        addCommonCAExts(cbld);
        // Make our Root CA Cert!
        rootCert = cbld.build(null, rootCaKP.getPrivate(),
                "SHA256withECDSA");
        log("Root CA Created:\n%s", certInfo(rootCert));

        // Now build a keystore and add the keys and cert
        rootKeystore = keyStoreBuilder.getKeyStore();
        Certificate[] rootChain = {rootCert};
        rootKeystore.setKeyEntry(ROOT_ALIAS, rootCaKP.getPrivate(),
                passwd.toCharArray(), rootChain);

        // Now fire up the OCSP responder
        rootOcsp = new SimpleOCSPServer(rootKeystore, passwd, ROOT_ALIAS, null);
        rootOcsp.enableLog(debug);
        rootOcsp.setNextUpdateInterval(3600);
        rootOcsp.setDisableContentLength(true);
        rootOcsp.start();

        // Wait 5 seconds for server ready
        boolean readyStatus = rootOcsp.awaitServerReady(5, TimeUnit.SECONDS);
        if (!readyStatus) {
            throw new RuntimeException("Server not ready");
        }

        rootOcspPort = rootOcsp.getPort();
        String rootRespURI = "http://localhost:" + rootOcspPort;
        log("Root OCSP Responder URI is %s", rootRespURI);

        // Now that we have the root keystore and OCSP responder we can
        // create our end entity certificate
        cbld.reset();
        cbld.setSubjectName("CN=SSLCertificate, O=SomeCompany");
        cbld.setPublicKey(eeKP.getPublic());
        cbld.setSerialNumber(new BigInteger("4096"));
        // Make a 1 year validity starting from 7 days ago
        start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        end = start + TimeUnit.DAYS.toMillis(365);
        cbld.setValidity(new Date(start), new Date(end));

        // Add extensions
        addCommonExts(cbld, eeKP.getPublic(), rootCaKP.getPublic());
        boolean[] kuBits = {true, false, false, false, false, false,
                false, false, false};
        cbld.addKeyUsageExt(kuBits);
        List<String> ekuOids = new ArrayList<>();
        ekuOids.add("1.3.6.1.5.5.7.3.1");
        ekuOids.add("1.3.6.1.5.5.7.3.2");
        cbld.addExtendedKeyUsageExt(ekuOids);
        cbld.addSubjectAltNameDNSExt(Collections.singletonList("localhost"));
        cbld.addAIAExt(Collections.singletonList(rootRespURI));
        // Make our End Entity Cert!
        eeCert = cbld.build(rootCert, rootCaKP.getPrivate(),
                "SHA256withECDSA");
        log("SSL Certificate Created:\n%s", certInfo(eeCert));

        // Provide end entity cert revocation info to the Root CA
        // OCSP responder.
        Map<BigInteger, SimpleOCSPServer.CertStatusInfo> revInfo =
                new HashMap<>();
        revInfo.put(eeCert.getSerialNumber(),
                new SimpleOCSPServer.CertStatusInfo(
                        SimpleOCSPServer.CertStatus.CERT_STATUS_GOOD));
        rootOcsp.updateStatusDb(revInfo);

        // Now build a keystore and add the keys, chain and root cert as a TA
        eeKeystore = keyStoreBuilder.getKeyStore();
        Certificate[] eeChain = {eeCert, rootCert};
        eeKeystore.setKeyEntry(EE_ALIAS, eeKP.getPrivate(),
                passwd.toCharArray(), eeChain);
        eeKeystore.setCertificateEntry(ROOT_ALIAS, rootCert);

        // And finally a Trust Store for the client
        trustStore = keyStoreBuilder.getKeyStore();
        trustStore.setCertificateEntry(ROOT_ALIAS, rootCert);
    }

    private static void addCommonExts(CertificateBuilder cbld,
            PublicKey subjKey, PublicKey authKey) throws IOException {
        cbld.addSubjectKeyIdExt(subjKey);
        cbld.addAuthorityKeyIdExt(authKey);
    }

    private static void addCommonCAExts(CertificateBuilder cbld)
            throws IOException {
        cbld.addBasicConstraintsExt(true, true, -1);
        // Set key usage bits for digitalSignature, keyCertSign and cRLSign
        boolean[] kuBitSettings = {true, false, false, false, false, true,
                true, false, false};
        cbld.addKeyUsageExt(kuBitSettings);
    }

    /**
     * Helper routine that dumps only a few cert fields rather than
     * the whole toString() output.
     *
     * @param cert an X509Certificate to be displayed
     *
     * @return the String output of the issuer, subject and
     * serial number
     */
    private static String certInfo(X509Certificate cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issuer: ").append(cert.getIssuerX500Principal()).
                append("\n");
        sb.append("Subject: ").append(cert.getSubjectX500Principal()).
                append("\n");
        sb.append("Serial: ").append(cert.getSerialNumber()).append("\n");
        return sb.toString();
    }

    /**
     * Log a message on stdout
     *
     * @param format the format string for the log entry
     * @param args zero or more arguments corresponding to the format string
     */
    private static void log(String format, Object ... args) {
        System.out.format(format + "\n", args);
    }
}