package listeners;

import model.IoTHubDeviceConectionStatus;

public interface IotHubConnectionStatusChangeCallbackListener
{
    void OnStatusChange(IoTHubDeviceConectionStatus iotHubDeviceConectionStatus);
}
