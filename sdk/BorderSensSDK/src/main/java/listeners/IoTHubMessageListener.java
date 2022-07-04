package listeners;

import model.IoTHubMessage;

public interface IoTHubMessageListener
{
    void OnCloudToDeviceMessageReceived(IoTHubMessage ioTHubMessage);
}
