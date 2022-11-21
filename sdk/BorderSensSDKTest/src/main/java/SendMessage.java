import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import org.json.JSONObject;
import services.impl.IoTConnectionDeviceServiceImpl;
import services.impl.IoTMessagesHandlerServiceImpl;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class SendMessage {
    public static void main(String[] args) {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "device-1-bs";
        Semaphore mutex = new Semaphore(1);
        try
        {
            String certificatesPath = "C:\\Users\\druiz\\repositorios\\BorderSens\\bordersens-sdk\\Certificates";
            String publicCertificate = certificatesPath + "\\"+idDevice+"-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\"+idDevice+"-private.pem";//"/new-device-01.key.pem";

            //String publicCertificate = certificatesPath + "\\device-2a-bs-public.pem";//"/new-device-01.cert.pem";
            //String privateCertificate = certificatesPath + "\\device-2a-bs-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate,privateCertificate);

            // JSONObject jMessage = new JSONObject(getRandomSample("samples-6"));

            JSONObject jMessage = new JSONObject(getRandomSampleFiltered("samples-6","REAL_SAMPLE_3"));
            jMessage.put("date",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss Z").format(new Date()));
            jMessage.put("value", UUID.randomUUID().toString());
            jMessage.put("sensor", idDevice);
            IoTMessagesHandlerServiceImpl messagesHandlerService = IoTMessagesHandlerServiceImpl.getInstance();
            final boolean[] send = {false};
            mutex.acquire();

            Timer timer = new Timer(4000,
                    (ActionListener) e -> mutex.release());
            timer.start();

            messagesHandlerService.sendMessageMQTT(securityProvider, iothubUri, idDevice, jMessage.toString(), new MessageSentCallback()
            {
                @Override
                public void onMessageSent(Message sentMessage, IotHubClientException clientException, Object callbackContext)
                {
                    System.out.println("Message sent!: \n\t" +new String(sentMessage.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
                    send[0] = true;
                    mutex.release();

                }
            });
            mutex.acquire();
            System.out.println("Saliendo");
            messagesHandlerService.stopCloudMessagesListener();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getRandomInt(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min) + min;
    }

    public static String getRandomSample(String samplesFolder) {
        URL folderURL = SendMessage.class.getClassLoader().getResource(samplesFolder);
        File folder = new File(folderURL.getFile());
        File[] listOfFiles = folder.listFiles();
        boolean isSelected = false;
        while (!isSelected) {
            int fileIndex = getRandomInt(0, listOfFiles.length);
            File file = listOfFiles[fileIndex];
            if (file != null && file.isFile()) {
                isSelected = true;
                FileReader reader = null;
                String content = null;
                try {
                    reader = new FileReader(file);
                    char[] chars = new char[(int) file.length()];
                    reader.read(chars);
                    content = new String(chars);
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if(reader != null){
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return content;
            }
        }
        return null;
    }

    public static String getRandomSampleFiltered(String samplesFolder,String filterName) {
        URL folderURL = SendMessage.class.getClassLoader().getResource(samplesFolder);
        File folder = new File(folderURL.getFile());
        File[] listOfFiles = folder.listFiles();
        List<File> lOfFilesFiltered = Arrays.asList(listOfFiles).stream().filter(f -> f.getName().toLowerCase().contains(filterName.toLowerCase())).collect(Collectors.toList());
        boolean isSelected = false;
        while (!isSelected) {
            int fileIndex = getRandomInt(0, lOfFilesFiltered.size());
            File file = lOfFilesFiltered.get(fileIndex);
            if (file != null && file.isFile()) {
                isSelected = true;
                FileReader reader = null;
                String content = null;
                try {
                    reader = new FileReader(file);
                    char[] chars = new char[(int) file.length()];
                    reader.read(chars);
                    content = new String(chars);
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if(reader != null){
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return content;
            }
        }
        return null;
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }
}
