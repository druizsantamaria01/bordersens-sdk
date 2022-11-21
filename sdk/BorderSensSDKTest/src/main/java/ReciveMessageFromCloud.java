import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import model.Counter;
import org.apache.commons.io.file.Counters;
import services.impl.IoTConnectionDeviceServiceImpl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;

public class ReciveMessageFromCloud {

    // Our MQTT doesn't support abandon/reject, so we will only display the messaged received
    // from IoTHub and return COMPLETE
    protected static class MessageCallbackMqtt implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message msg, Object context)
        {
            Counters.Counter counter = (Counters.Counter) context;
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

    public static void main(String[] args) {
        String iothubUri = "bs-iothub-service.azure-devices.net";
        String idDevice = "bs-amsterdam-device-1";
        DeviceClient deviceClient = null;
        try
        {
            // Rutas a los certificados del dispositivo
            String certificatesPath = "C:\\Users\\druiz\\repositorios\\BorderSens\\Certificados";
            String publicCertificate = certificatesPath + "\\bs-amsterdam-device-1-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\bs-amsterdam-device-1-private.pem";//"/new-device-01.key.pem";
            SecurityProvider securityProvider = IoTConnectionDeviceServiceImpl.getSecurityProviderX509(publicCertificate,privateCertificate); // Security Provider
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
    }
}
