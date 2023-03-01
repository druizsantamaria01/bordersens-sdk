package sdkutils;

import com.google.gson.JsonObject;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import model.SecurityProviderX509Cert;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utilities
{
    public static Key parsePrivateKey(String privateKeyString) throws IOException
    {
        Security.addProvider(new BouncyCastleProvider());
        PEMParser privateKeyParser = new PEMParser(new StringReader(privateKeyString));
        Object possiblePrivateKey = privateKeyParser.readObject();
        return getPrivateKey(possiblePrivateKey);
    }

    public static X509Certificate parsePublicKeyCertificate(String publicKeyCertificateString) throws IOException, CertificateException
    {
        Security.addProvider(new BouncyCastleProvider());
        PemReader publicKeyCertificateReader = new PemReader(new StringReader(publicKeyCertificateString));
        PemObject possiblePublicKeyCertificate = publicKeyCertificateReader.readPemObject();
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(possiblePublicKeyCertificate.getContent()));
    }

    public static Key getPrivateKey(Object possiblePrivateKey) throws IOException
    {
        if (possiblePrivateKey instanceof PEMKeyPair)
        {
            return new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) possiblePrivateKey)
                    .getPrivate();
        }
        else if (possiblePrivateKey instanceof PrivateKeyInfo)
        {
            return new JcaPEMKeyConverter().getPrivateKey((PrivateKeyInfo) possiblePrivateKey);
        }
        else
        {
            throw new IOException("Unable to parse private key, type unknown");
        }
    }

    public static String readCertificateFromPath(String filePath) throws IOException
    {
        Path path = Paths.get(filePath);
        BufferedReader bufferedReader = Files.newBufferedReader(path);
        StringBuffer sb = new StringBuffer();
        String curLine;
        while ((curLine = bufferedReader.readLine()) != null){
            sb.append(curLine+"\n");
        }
        bufferedReader.close();
        return sb.toString();
    }

    public static String readProperty(String key,String defaultValue) {
        try (InputStream input = Utilities.class.getClassLoader().getResourceAsStream("configSDK.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                return defaultValue;
            }
            prop.load(input);
            String value = prop.getProperty(key);
            if (value==null)
                return defaultValue;
            else
                return value;
        } catch (IOException ex) {
            return defaultValue;
        }
    }

    public static List<JsonObject> readComplexProperty(String prefix) {
        List<JsonObject> response = new ArrayList<>();
        try (InputStream input = Utilities.class.getClassLoader().getResourceAsStream("configSDK.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            List<String> keys = prop.keySet().stream().
                    map(v -> String.valueOf(v))
                    .filter(k -> k.toString().startsWith(prefix)).collect(Collectors.toList());
            Set<String> indexs = keys.stream().map(k -> extractOneWithRegex(k.replaceFirst(prefix,""),"\\d+")).collect(Collectors.toSet());
            for (String index : indexs) {
                String toRemove = prefix + "." +index + ".";
                List<String> filteredKeys = keys.stream().filter(k -> k.startsWith(toRemove)).collect(Collectors.toList());
                JsonObject jItem = new JsonObject();
                jItem.addProperty("index",index);
                for (String key : filteredKeys) {
                    String attName = key.replaceFirst(toRemove,"");
                    jItem.addProperty(attName,prop.getProperty(key));
                }
                response.add(jItem);
            }
            return response;
        } catch (IOException ex) {
            return response;
        }
    }

    public static String extractOneWithRegex(String text,String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        while(m.find()) {
            String x = m.group();
            return m.group();
        }
        return null;
    }

    public boolean checkCertificates(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath) {
        return false;
    }

    public static X509Certificate getCertificate(String path) throws CertificateException, IOException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream(path);
        X509Certificate cert = (X509Certificate) fact.generateCertificate(fis);
        fis.close();
        return cert;
    }

    public static  boolean isNullString(String s) {
        return s == null | s.equals("");
    }

    public static int getMinLength(String a, String b) {
        if (isNullString(a) || isNullString(b))
            return -1;
        else
            return Math.min(a.length(),b.length());
    }

    public static boolean isTwoJsonEquals(JsonObject firstObj,JsonObject secondObj, JSONCompareMode compareMode){
        List<String> keys1 = firstObj.entrySet().stream().map(i -> i.getKey()).collect(Collectors.toCollection(ArrayList::new));
        List<String> keys2 = secondObj.entrySet().stream().map(i -> i.getKey()).collect(Collectors.toCollection(ArrayList::new));

        for (String k : CollectionUtils.disjunction(keys1, keys2)) {
            firstObj.remove(k);
            secondObj.remove(k);
        }

        try {
            JSONCompareResult result = JSONCompare.compareJSON(new JSONObject(firstObj.toString()), new JSONObject(secondObj.toString()), compareMode);

            return result.passed();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static JsonObject merge(JsonObject firstObj,JsonObject secondObj) {
        List<String> keys1 = firstObj.entrySet().stream().map(i -> i.getKey()).collect(Collectors.toCollection(ArrayList::new));
        List<String> keys2 = secondObj.entrySet().stream().map(i -> i.getKey()).collect(Collectors.toCollection(ArrayList::new));
        keys1.retainAll(keys2);
        for (String k : keys1) {
            firstObj.add(k,secondObj.get(k));
        }
        return firstObj;
    }

    public static SecurityProvider getSecurityProviderX509(String publicDeviceCertificatePath, String privateDeviceCertificatePath) throws IOException, CertificateException
    {
        String leafPublicPem = Utilities.readCertificateFromPath(publicDeviceCertificatePath);
        String leafPrivateKeyPem = Utilities.readCertificateFromPath(privateDeviceCertificatePath);

        X509Certificate leafPublicCert = Utilities.parsePublicKeyCertificate(leafPublicPem);
        Key leafPrivateKey = Utilities.parsePrivateKey(leafPrivateKeyPem);
        SecurityProvider securityProviderX509= new SecurityProviderX509Cert(leafPublicCert, leafPrivateKey, new ArrayList());
        return securityProviderX509;
    }
}
