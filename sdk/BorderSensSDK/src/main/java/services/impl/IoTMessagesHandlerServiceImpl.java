package services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import listeners.IoTHubMessageListener;
import listeners.IotHubConnectionStatusChangeCallbackListener;
import model.Counter;
import model.IoTHubDeviceConectionStatus;
import model.IoTHubMessage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import services.IoTMessagesHandlerService;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class IoTMessagesHandlerServiceImpl implements IoTMessagesHandlerService
{

    private static IoTMessagesHandlerServiceImpl instance;

    private static final String KO = "KO";
    private static final String OK = "OK";

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
    public void sendMessageMQTT(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, MessageSentCallback callback) throws Exception {
        deviceClient = null;
        try
        {
            deviceClient = new DeviceClient(iotHubUri, idDevice, securityProvider, IotHubClientProtocol.MQTT);
            deviceClient.open(false);
            Message messageToSendFromDeviceToHub = new Message(message);
            System.out.println("Sending message from device to IoT Hub...");

            deviceClient.sendEventAsync(messageToSendFromDeviceToHub, new MessageSentCallback() {
                @Override
                public void onMessageSent(Message message, IotHubClientException e, Object o) {
                    callback.onMessageSent(message,e,o);
                }
            }, null);

        } catch (IOException e)
        {
            e.printStackTrace();
            if (deviceClient != null)
            {
                deviceClient.close();
                throw new IOException("Certificate reading error");
            }
        }
        catch (IotHubClientException e)
        {
            e.printStackTrace();
            if (deviceClient != null)
            {
                deviceClient.close();
                throw new IotHubClientException(e.getStatusCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            if (deviceClient != null)
            {
                deviceClient.close();
                throw new Exception(e.getMessage());
            }
        }
    }

    @Override
    public void startCloudMessagesListener(SecurityProvider securityProvider, String iotHubUri, String idDevice, IotHubConnectionStatusChangeCallbackListener connectionListener, IoTHubMessageListener messagesListener) throws IOException, IotHubClientException
    {
        try {
            if (deviceClient == null)
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

                    /*
                    if (status.getStatus() == IotHubConnectionStatus.CONNECTED)
                        mutex.release();
                     */
                }
            }, new Object());

            deviceClient.open(false);
            // mutex.acquire();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopCloudMessagesListener()
    {
        if (deviceClient!=null) {
            deviceClient.close();
        }
    }

    @Override
    public Map<String,Object> sendSyncMessage(SecurityProvider securityProvider, String iotHubUri, String idDevice, String message, int millisecondsToTimeout) {
        AtomicReference<Map<String,Object>> response = new AtomicReference<>(new HashMap<>());
        JsonObject jMessage= new JsonParser().parse(message).getAsJsonObject();
        String idSample = (jMessage.has("value"))?jMessage.get("value").getAsString():"NONE";
        Semaphore mutexSend = new Semaphore(1);
        AtomicReference<String> a = new AtomicReference<>("");

        // Message Send Block
        try {
            mutexSend.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Timer timerSend = new Timer(4000,
                (ActionListener) e -> {
            response.set(generateErrorResponse(idDevice,idSample,new TimeoutException("Timeout on sent to IoT Hub"), "Timeout occurred when sending the message to IoTHub"));
            mutexSend.release();
        });
        timerSend.start();
        try {
            sendMessageMQTT(securityProvider, iotHubUri, idDevice, message, new MessageSentCallback() {
                @Override
                public void onMessageSent(Message message, IotHubClientException e, Object o) {
                    System.out.println("Message sent!: \n\t" +new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
                    mutexSend.release();
                }
            });
            mutexSend.acquire();
        } catch (Exception e) {
            response.set(generateErrorResponse(idDevice,idSample,e, "Timeout occurred when sending the message to IoTHub"));
            mutexSend.release();
        }
        Semaphore mutexSemaphore = new Semaphore(1);
        try
        {
            mutexSemaphore.acquire();
            System.out.println();
            Timer timer = new Timer(millisecondsToTimeout,
                    (ActionListener) e -> {
                response.set(generateErrorResponse(idDevice,idSample,new TimeoutException("Timeout"), "Timeout occurred when is waiting the response"));
                mutexSemaphore.release();
            });
            timer.start();

            startCloudMessagesListener(
                    securityProvider,
                    iotHubUri,
                    idDevice,
                    iotHubDeviceConectionStatus ->
                    {
                        System.out.println("Status: " +iotHubDeviceConectionStatus.getStatus().toString());
                        System.out.println("ChangeReason: " +iotHubDeviceConectionStatus.getStatusChangeReason().toString());
                    },
                    ioTHubMessage ->  {
                        System.out.println("Counter: " + ioTHubMessage.getCounter().toString() + ", Message: " + ioTHubMessage.getMessageStr());
                        JsonObject jResponse = JsonParser.parseString(ioTHubMessage.getMessageStr()).getAsJsonObject();
                        response.set(new Gson().fromJson(jResponse,Map.class));
                        mutexSemaphore.release();
                    }
            );
            mutexSemaphore.acquire();
            stopCloudMessagesListener();
        }
        catch (IOException e)
        {
            response.set(generateErrorResponse(idDevice,idSample,e, "IOException occurred when is waiting the response"));
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            response.set(generateErrorResponse(idDevice,idSample,e, "InterruptedException occurred when is waiting the response"));
            e.printStackTrace();
        }
        catch (IotHubClientException e)
        {
            response.set(generateErrorResponse(idDevice,idSample,e, "IotHubClientException occurred when is waiting the response"));
            e.printStackTrace();
        }
        return response.get();
    }

    private Map<String,Object> generateErrorResponse(String idDevice, String idSample,Exception e, String cause) {

        SimpleDateFormat iso8601Formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");

        // Main fields
        JsonObject jResponse = new JsonObject();
        jResponse.addProperty("status",KO);
        jResponse.addProperty("device",idDevice);
        jResponse.addProperty("idSample",idSample);

        // Steps data
        JsonArray jSteps = new JsonArray();
        JsonObject jStep = new JsonObject();
        jStep.addProperty("stepName","SEND");
        jStep.addProperty("datatime",iso8601Formatter.format(new Date()));
        jSteps.add(jStep);
        jResponse.add("steps",jSteps);

        // Inference data
        jResponse.add("inference",null);

        // Error data
        JsonObject jError = new JsonObject();
        jError.addProperty("errorType","SEND");
        jError.addProperty("errorName",e.getClass().getSimpleName());
        JsonObject jErrorDetails = new JsonObject();
        jErrorDetails.addProperty("error",e.getMessage());
        jErrorDetails.addProperty("cause",cause);
        jErrorDetails.addProperty("trace", ExceptionUtils.getStackTrace(e));
        jError.add("details",jErrorDetails);
        jResponse.add("error",jError);
        return new Gson().fromJson(jResponse,Map.class);
    }
}
