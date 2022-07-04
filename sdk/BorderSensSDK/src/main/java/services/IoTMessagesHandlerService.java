package services;

import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import listeners.IoTHubMessageListener;
import listeners.IotHubConnectionStatusChangeCallbackListener;

import java.io.IOException;

public interface IoTMessagesHandlerService
{
    void sendMessageMQTT(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, MessageSentCallback callback);

    void startCloudMessagesListener(SecurityProvider securityProvider, String iotHubUri, String idDevice, IotHubConnectionStatusChangeCallbackListener connectionListener, IoTHubMessageListener messagesListener) throws IOException, IotHubClientException;

    void stopCloudMessagesListener();
}
