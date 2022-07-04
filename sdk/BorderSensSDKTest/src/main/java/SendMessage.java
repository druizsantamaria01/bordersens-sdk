import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import org.json.JSONObject;
import services.impl.IoTConnectionDeviceServiceImpl;
import services.impl.IoTMessagesHandlerServiceImpl;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class SendMessage {
    public static void main(String[] args) {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "device-1-bs";
        Semaphore mutex = new Semaphore(1);
        try
        {
            String certificatesPath = "C:\\Users\\danie\\repositorios\\BorderSens\\create-certificates\\azure-iot-sdk-c\\tools\\CACertificates";
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate,privateCertificate);

            JSONObject jMessage = new JSONObject(getRandomSample());
            jMessage.put("date",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss").format(new Date()));
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
                    System.out.println("Message sent!");
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

    public static String getRandomSample() {
        URL folderURL = SendMessage.class.getClassLoader().getResource("samples");
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

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }
}
