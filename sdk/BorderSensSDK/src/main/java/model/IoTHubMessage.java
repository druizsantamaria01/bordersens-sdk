package model;

import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.Message;

public class IoTHubMessage
{
    private Message message;
    private Counter counter;
    private IotHubMessageResult result;

    public IoTHubMessage(Message msg, Object context)
    {
        this.counter = ((Counter) context);
        this.message = msg;
    }

    public IoTHubMessage(Message message, Counter counter, IotHubMessageResult result)
    {
        this.message = message;
        this.counter = counter;
        this.result = result;
    }

    public Message getMessage()
    {
        return message;
    }

    public String getMessageStr() {
        return (this.message!=null)?new String(this.message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET):null;
    }

    public void setMessage(Message message)
    {
        this.message = message;
    }

    public Counter getCounter()
    {
        return counter;
    }

    public int getCounterInt()
    {
        return counter.get();
    }

    public void setCounter(Counter counter)
    {
        this.counter = counter;
    }

    public IotHubMessageResult getResult()
    {
        return result;
    }

    public void setResult(IotHubMessageResult result)
    {
        this.result = result;
    }
}
