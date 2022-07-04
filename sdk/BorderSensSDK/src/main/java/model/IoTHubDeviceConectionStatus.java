package model;

import com.microsoft.azure.sdk.iot.device.ConnectionStatusChangeContext;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;

public class IoTHubDeviceConectionStatus
{
    private IotHubConnectionStatus status;
    private IotHubConnectionStatusChangeReason statusChangeReason;
    private Throwable throwable;

    public IoTHubDeviceConectionStatus(ConnectionStatusChangeContext connectionStatusChangeContext)
    {
        this.status = connectionStatusChangeContext.getNewStatus();
        this.statusChangeReason = connectionStatusChangeContext.getNewStatusReason();
        this.throwable = connectionStatusChangeContext.getCause();
    }

    public IotHubConnectionStatus getStatus()
    {
        return status;
    }

    public void setStatus(IotHubConnectionStatus status)
    {
        this.status = status;
    }

    public IotHubConnectionStatusChangeReason getStatusChangeReason()
    {
        return statusChangeReason;
    }

    public void setStatusChangeReason(IotHubConnectionStatusChangeReason statusChangeReason)
    {
        this.statusChangeReason = statusChangeReason;
    }

    public Throwable getThrowable()
    {
        return throwable;
    }

    public void setThrowable(Throwable throwable)
    {
        this.throwable = throwable;
    }
}
