package sdkutils;


import org.junit.jupiter.api.Test;

import javax.naming.InvalidNameException;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.*;


import static org.bouncycastle.util.encoders.Hex.toHexString;

import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.bouncycastle.pqc.math.linearalgebra.ByteUtils.toHexString;

class UtilitiesTest {

    @Test
    void readProperty() {
        String property = Utilities.readProperty("mongo.host","no encontrado");
        System.out.println(property);
    }

    @Test
    void checkCertificates() throws URISyntaxException, CertificateException, IOException, InvalidNameException, NoSuchAlgorithmException, InvalidKeySpecException {

        String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
        String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
        String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";

        String privateKeyContent = new String(Files.readAllBytes(Paths.get(privateCertificate)));
        String publicKeyContent = new String(Files.readAllBytes(Paths.get(publicCertificate)));
        privateKeyContent = privateKeyContent.replaceAll("\\n", "").replaceAll("\\r", "").replace("-----BEGIN PRIVATE KEY-----", "").replaceAll("-----(.*?)-----", "");
        publicKeyContent = publicKeyContent.replaceAll("\\n", "").replaceAll("\\r", "").replace("-----BEGIN PUBLIC KEY-----", "").replaceAll("-----(.*?)-----", "");;

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyContent));
        PrivateKey privKey = kf.generatePrivate(keySpecPKCS8);

        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));
        RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);

        System.out.println();

        /*
        String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
        String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
        String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
        String intermediateCertificate = certificatesPath + "\\Intermediate1-intermediate-test-bs-group.pem";//"/azure-iot-test-only.intermediate.cert.pem";

        X509Certificate publicCert = Utils.getCertificate(publicCertificate);
        // X509Certificate privateCert = Utils.getCertificate(privateCertificate);
        X509Certificate intermediateCert = Utils.getCertificate(intermediateCertificate);
        System.out.println();


        try {
            final PemReader certReader = new PemReader(new FileReader(publicCertificate));
            final PemObject certAsPemObject = certReader.readPemObject();
            if (!certAsPemObject.getType().equalsIgnoreCase("CERTIFICATE")) {
                throw new IllegalArgumentException("Certificate file does not contain a certificate but a " + certAsPemObject.getType());
            }
            final byte[] x509Data = certAsPemObject.getContent();
            final CertificateFactory fact = CertificateFactory.getInstance("X509");
            final Certificate cert = fact.generateCertificate(new ByteArrayInputStream(x509Data));
            if (!(cert instanceof X509Certificate)) {
                throw new IllegalArgumentException("Certificate file does not contain an X509 certificate");
            }

            final PublicKey publicKey = cert.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey)) {
                throw new IllegalArgumentException("Certificate file does not contain an RSA public key but a " + publicKey.getClass().getName());
            }

            final RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            final byte[] certModulusData = rsaPublicKey.getModulus().toByteArray();

            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            final byte[] certID = sha1.digest(certModulusData);
            final String certIDinHex = Hex.toHexString(certID);


            final PemReader keyReader = new PemReader(new FileReader(privateCertificate));
            final PemObject keyAsPemObject = keyReader.readPemObject();
            if (!keyAsPemObject.getType().equalsIgnoreCase("RSA PRIVATE KEY")) {
                throw new IllegalArgumentException("Key file does not contain a private key but a " + keyAsPemObject.getType());
            }

            final byte[] privateKeyData = keyAsPemObject.getContent();
            final KeyFactory keyFact = KeyFactory.getInstance("RSA");
            final KeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyData);
            final PrivateKey privateKey = keyFact.generatePrivate(keySpec);
            if (!(privateKey instanceof RSAPrivateKey)) {
                throw new IllegalArgumentException("Key file does not contain an X509 encoded private key");
            }
            final RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;
            final byte[] keyModulusData = rsaPrivateKey.getModulus().toByteArray();
            final byte[] keyID = sha1.digest(keyModulusData);
            final String keyIDinHex = Hex.toHexString(keyID);

            System.out.println(publicCertificate + " : " + certIDinHex);
            System.out.println(privateCertificate + " : " + keyIDinHex);
            if (certIDinHex.equalsIgnoreCase(keyIDinHex)) {
                System.out.println("Match");
                System.exit(0);
            } else {
                System.out.println("No match");
                System.exit(-1);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(-2);
        }



        */
        /*
        PublicKey key = cer.getPublicKey();
        String dn = cer.getSubjectX500Principal().getName();
        LdapName ldapDN = new LdapName(dn);
        for(Rdn rdn: ldapDN.getRdns()) {
            System.out.println(rdn.getType() + " -> " + rdn.getValue());
        }
        is.close();
         */
    }
}