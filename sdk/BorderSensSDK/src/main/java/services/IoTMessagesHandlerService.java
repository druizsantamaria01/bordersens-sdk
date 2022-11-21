package services;

import com.google.gson.JsonObject;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import listeners.IoTHubMessageListener;
import listeners.IotHubConnectionStatusChangeCallbackListener;

import java.io.IOException;
import java.util.Map;

public interface IoTMessagesHandlerService
{
    void sendMessageMQTT(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, MessageSentCallback callback) throws Exception;

    void startCloudMessagesListener(SecurityProvider securityProvider, String iotHubUri, String idDevice, IotHubConnectionStatusChangeCallbackListener connectionListener, IoTHubMessageListener messagesListener) throws IOException, IotHubClientException;

    void stopCloudMessagesListener();

    Map<String,Object> sendSyncMessage(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, int millisecondsToTimeout) throws InterruptedException;
}
