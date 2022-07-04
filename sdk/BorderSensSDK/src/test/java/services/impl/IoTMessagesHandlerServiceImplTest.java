package services.impl;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import services.IoTMessagesHandlerService;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class IoTMessagesHandlerServiceImplTest {
    @Test
    void sendMessageMQTT()
    {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "device-1-bs";
        Semaphore mutex = new Semaphore(1);
        try
        {
            String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate,privateCertificate);
            assertTrue(securityProvider!=null);

            JSONObject jMessage = new JSONObject();
            jMessage.put("type","telemetry");
            jMessage.put("temperature",getRandomInt(-10,40));
            jMessage.put("pressure",getRandomInt(1,10));

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
            assertTrue(send[0]);

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }


    protected static class Counter
    {
        protected int num;

        public Counter(int num)
        {
            this.num = num;
        }

        public int get()
        {
            return this.num;
        }

        public void increment()
        {
            this.num++;
        }

        @Override
        public String toString()
        {
            return Integer.toString(this.num);
        }
    }
    // Our MQTT doesn't support abandon/reject, so we will only display the messaged received
    // from IoTHub and return COMPLETE
    protected static class MessageCallbackMqtt implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message msg, Object context)
        {
            Counter counter = (Counter) context;
            System.out.println(
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            for (MessageProperty messageProperty : msg.getProperties())
            {
                System.out.println(messageProperty.getName() + " : " + messageProperty.getValue());
            }

            counter.increment();

            return IotHubMessageResult.COMPLETE;
        }
    }

    protected static class IotHubConnectionStatusChangeCallbackLogger implements IotHubConnectionStatusChangeCallback
    {
        @Override
        public void onStatusChanged(ConnectionStatusChangeContext connectionStatusChangeContext)
        {
            IotHubConnectionStatus status = connectionStatusChangeContext.getNewStatus();
            IotHubConnectionStatusChangeReason statusChangeReason = connectionStatusChangeContext.getNewStatusReason();
            Throwable throwable = connectionStatusChangeContext.getCause();

            System.out.println();
            System.out.println("CONNECTION STATUS UPDATE: " + status);
            System.out.println("CONNECTION STATUS REASON: " + statusChangeReason);
            System.out.println("CONNECTION STATUS THROWABLE: " + (throwable == null ? "null" : throwable.getMessage()));
            System.out.println();

            if (throwable != null)
            {
                throwable.printStackTrace();
            }

            if (status == IotHubConnectionStatus.DISCONNECTED)
            {
                System.out.println("The connection was lost, and is not being re-established." +
                        " Look at provided exception for how to resolve this issue." +
                        " Cannot send messages until this issue is resolved, and you manually re-open the device client");
            }
            else if (status == IotHubConnectionStatus.DISCONNECTED_RETRYING)
            {
                System.out.println("The connection was lost, but is being re-established." +
                        " Can still send messages, but they won't be sent until the connection is re-established");
            }
            else if (status == IotHubConnectionStatus.CONNECTED)
            {
                System.out.println("The connection was successfully established. Can send messages.");
            }
        }
    }

    /*
    @Test
    void connectCloudToReciveMessages() {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "device-1-bs";
        Semaphore mutexSemaphore = new Semaphore(1);
        try
        {
            mutexSemaphore.acquire();
            String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate, privateCertificate);
            assertTrue(securityProvider != null);

            Timer timer = new Timer(20000,
                    (ActionListener) e -> mutexSemaphore.release());
            timer.start();

            IoTMessagesHandlerService msgService = IoTMessagesHandlerServiceImpl.getInstance();
            msgService.startCloudMessagesListener(
                    securityProvider,
                    iothubUri,
                    idDevice,
                    iotHubDeviceConectionStatus ->
                    {
                        System.out.println("Status: " +iotHubDeviceConectionStatus.getStatus().toString());
                        System.out.println("ChangeReason: " +iotHubDeviceConectionStatus.getStatusChangeReason().toString());
                    },
                    ioTHubMessage -> System.out.println("Counter: " + ioTHubMessage.getCounter().toString() + ", Message: " + ioTHubMessage.getMessageStr())
            );
            mutexSemaphore.acquire();
            msgService.stopCloudMessagesListener();


        }
        catch (CertificateException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        catch (IotHubClientException e)
        {
            e.printStackTrace();
        }
    }

     */

    /*
    @Test
    void reciveMessageMQTTOther()
    {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "device-1-bs";
        DeviceClient deviceClient = null;
        try
        {
            String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate,privateCertificate);
            deviceClient = new DeviceClient(iothubUri, idDevice, securityProvider, IotHubClientProtocol.MQTT);

            System.out.println("Successfully created an IoT Hub client.");
            MessageCallbackMqtt callback = new MessageCallbackMqtt();
            Counter counter = new Counter(0);
            deviceClient.setMessageCallback(callback, counter);
            System.out.println("Successfully set message callback.");

            deviceClient.setConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallbackLogger(), new Object());

            deviceClient.open(false);

            System.out.println("Opened connection to IoT Hub.");
            System.out.println("Beginning to receive messages...");

            // Wait for IoT Hub to respond.
            try
            {
                Thread.sleep(1000000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }


        } catch (IOException e)
        {
            e.printStackTrace();
            if (deviceClient != null)
            {
                deviceClient.close();
            }
        }
        catch (IotHubClientException e)
        {
            e.printStackTrace();
            if (deviceClient != null)
            {
                deviceClient.close();
            }
        }
        catch (CertificateException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }

    }
     */

    public int getRandomInt(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min) + min;
    }
}