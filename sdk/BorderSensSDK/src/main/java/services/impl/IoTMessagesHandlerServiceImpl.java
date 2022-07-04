package services.impl;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import listeners.IoTHubMessageListener;
import listeners.IotHubConnectionStatusChangeCallbackListener;
import model.Counter;
import model.IoTHubDeviceConectionStatus;
import model.IoTHubMessage;
import services.IoTMessagesHandlerService;

import java.io.IOException;

public class IoTMessagesHandlerServiceImpl implements IoTMessagesHandlerService
{

    private static IoTMessagesHandlerServiceImpl instance;

    private DeviceClient deviceClient;

    private IoTMessagesHandlerServiceImpl()
    {

    }

    public static IoTMessagesHandlerServiceImpl getInstance(){
        if (instance == null)
            instance = new IoTMessagesHandlerServiceImpl();
        return instance;
    }

    @Override
    public void sendMessageMQTT(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, MessageSentCallback callback)
    {
        deviceClient = null;
        try
        {
            deviceClient = new DeviceClient(iotHubUri, idDevice, securityProvider, IotHubClientProtocol.MQTT);
            deviceClient.open(false);
            Message messageToSendFromDeviceToHub = new Message(message);
            System.out.println("Sending message from device to IoT Hub...");
            deviceClient.sendEventAsync(messageToSendFromDeviceToHub,callback,null);

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
    }

    @Override
    public void startCloudMessagesListener(SecurityProvider securityProvider, String iotHubUri, String idDevice, IotHubConnectionStatusChangeCallbackListener connectionListener, IoTHubMessageListener messagesListener) throws IOException, IotHubClientException
    {
        deviceClient = new DeviceClient(iotHubUri, idDevice, securityProvider, IotHubClientProtocol.MQTT);
        System.out.println("Successfully created an IoT Hub client.");
        deviceClient.setMessageCallback(new MessageCallback()
        {
            @Override
            public IotHubMessageResult onCloudToDeviceMessageReceived(Message message, Object callbackContext)
            {
                IoTHubMessage msg = new IoTHubMessage(message, callbackContext);
                System.out.println("Received message: " + msg.getMessage());
                if (messagesListener!=null) {
                    messagesListener.OnCloudToDeviceMessageReceived(msg);
                }
                msg.getCounter().increment();
                return IotHubMessageResult.COMPLETE;
            }
        },new Counter(0));

        deviceClient.setConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallback()
        {
            @Override
            public void onStatusChanged(ConnectionStatusChangeContext connectionStatusChangeContext)
            {
                IoTHubDeviceConectionStatus status  = new IoTHubDeviceConectionStatus(connectionStatusChangeContext);
                if (connectionListener!=null) {
                    connectionListener.OnStatusChange(status);
                }
            }
        }, new Object());

        deviceClient.open(false);
    }

    @Override
    public void stopCloudMessagesListener()
    {
        if (deviceClient!=null) {
            deviceClient.close();
        }
    }
}
