package services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import events.BorderSensSDKServiceEvents;
import events.DockerContainerEvent;
import events.InternetStateEvent;
import events.SynchronizeDataEvent;
import model.ContainerStatus;
import model.DataContainerRepository;
import model.DeployContainerStatus;
import model.ProvisioningStatus;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import services.BorderSensSDK;
import sdkutils.Utilities;
import services.IoTMessagesHandlerService;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BorderSensSDKService implements BorderSensSDK {

    private static BorderSensSDKService instance;

    private String idDevice;

    private String iotHubUri;
    private int pendingDataToSynchronize;
    private DockerContainerManagerImp dockerManager;

    private DataSynchronizationManagerImp dataManager;

    private ConnectivityMonitorService connectivityMonitorService;

    private IoTMessagesHandlerServiceImpl ioTMessagesHandlerService;

    private LocalMassagesHandlerServiceImp localMassagesHandlerServiceImp;

    private IoTConnectionDeviceServiceImpl ioTConnectionDeviceService;

    private List<BorderSensSDKServiceEvents> listeners;

    private boolean isInitialized = false;

    private boolean isConnected = false;

    private final static Logger logger = Logger.getLogger("BorderSensSDKService");


    public static BorderSensSDKService getInstance(String idDevice) {
        if (instance==null) {
            instance = new BorderSensSDKService(idDevice);
        }
        return instance;
    }

    public static BorderSensSDKService getInstance(String idDevice,BorderSensSDKServiceEvents listener) {
        if (instance==null) {
            instance = new BorderSensSDKService(idDevice);
        }
        instance.listeners.add(listener);
        return instance;
    }

    private BorderSensSDKService(String idDevice) {
        this.idDevice = idDevice;
        this.iotHubUri = Utilities.readProperty("iothub.uri","bs-iothub-service.azure-devices.net");
        this.pendingDataToSynchronize = 0;
        this.listeners = new ArrayList<>();
        this.dockerManager = DockerContainerManagerImp.getInstance(new DockerContainerEvent() {
            @Override
            public void onDockerStateChange(boolean isDockerRunning) {
                // logger.log(Level.INFO, String.format("On docker state Change: %s",isDockerRunning));
                for (BorderSensSDKServiceEvents l : listeners) {
                    l.onDockerStateChange(isDockerRunning);
                }
            }

            @Override
            public void onDockerContainerIsDeployed(String image, DeployContainerStatus status, ContainerStatus containerStatus) {
                // logger.log(Level.INFO, String.format("On Image: %s has been deployed\nDeployContainerStatus: %s\nContainerStatus: %s",image, status.toString(),containerStatus.toString()));
                for (BorderSensSDKServiceEvents l : listeners) {
                    l.onDockerContainerIsDeployed(image, status, containerStatus);
                }
            }
        });
        this.dataManager = DataSynchronizationManagerImp.getInstance(this.idDevice, new SynchronizeDataEvent() {
            @Override
            public void OnDataSynchronizedIsDone(JsonObject jReponse) {
                // logger.log(Level.INFO, String.format("Synchronized data: %s is finished at ",jReponse.toString()));
                for (BorderSensSDKServiceEvents l : listeners) {
                    l.OnDataSynchronizedIsDone(jReponse);
                }
            }
        });

        this.connectivityMonitorService = ConnectivityMonitorService.getInstance(new InternetStateEvent() {
            @Override
            public void onChangeConnectionState(boolean conn) {
                isConnected = conn;
            }
        });
        this.isConnected = connectivityMonitorService.isConected();

        this.ioTMessagesHandlerService = IoTMessagesHandlerServiceImpl.getInstance();
        this.localMassagesHandlerServiceImp = LocalMassagesHandlerServiceImp.getInstance();
        this.ioTConnectionDeviceService = new IoTConnectionDeviceServiceImpl(
                Utilities.readProperty("iothub.dps.idScope","0ne005D00DC"),
                Utilities.readProperty("iothub.dps.globalendpoint","bs-iothub-service.azure-devices.net")
        );
    }

    public String getIdDevice() {
        return idDevice;
    }

    public String getIotHubUri() {
        return iotHubUri;
    }

    public int getPendingDataToSynchronize() {
        return pendingDataToSynchronize;
    }

    public DockerContainerManagerImp getDockerManager() {
        return dockerManager;
    }

    public DataSynchronizationManagerImp getDataManager() {
        return dataManager;
    }

    public ConnectivityMonitorService getConnectivityMonitorService() {
        return connectivityMonitorService;
    }

    public IoTMessagesHandlerServiceImpl getIoTMessagesHandlerService() {
        return ioTMessagesHandlerService;
    }

    public LocalMassagesHandlerServiceImp getLocalMassagesHandlerServiceImp() {
        return localMassagesHandlerServiceImp;
    }

    public IoTConnectionDeviceServiceImpl getIoTConnectionDeviceService() {
        return ioTConnectionDeviceService;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void initialize(){
        if (!isInitialized) {
            this.dockerManager.initialize();
            boolean isRunningContainers = false;
            int maxTime = 10000;
            int currentTime = 0;
            while (!this.dockerManager.checkStatus()) {
                try {
                    logger.log(Level.INFO,String.format("Waiting %d milliseconds for check container state",currentTime));
                    currentTime += 500;
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            logger.log(Level.INFO,String.format("Check complete, %d milliseconds",currentTime));
            this.dockerManager.checkStatus();
            this.dataManager.startSynchronization(Integer.parseInt(Utilities.readProperty("data.secondsToStartSynchronize", "60")), Integer.parseInt(Utilities.readProperty("data.secondsToSynchronize", "60")));
            this.isInitialized = true;
            for (BorderSensSDKServiceEvents l : listeners) {
                l.OnBorderSensSDKServiceIsInitialized(dockerManager.isDockerManagerReady(), dataManager.isSynchronized());
            }
        }
    }

    public boolean checkStatus() {
        for (DataContainerRepository dcr : dockerManager.containerRepositories) {
            for (DataContainerRepository.ImagesContainer image : dcr.getImages().values()) {
                if (image.getHealthEndpoint()!=null && !image.getHealthEndpoint().equals("")) {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(image.getHealthEndpoint()).build();
                    try {
                        Response response = client.newCall(request).execute();
                        if (response.code()!=200) {
                            response.close();
                            return false;
                        }
                    } catch (IOException e) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    @Override
    public Map<String,Object> sendSyncMessage(SecurityProvider securityProvider, String message) {
        boolean isReady = checkStatus();
        if (!isInitialized) {
            initialize();
        }
        Map<String,Object> response = new HashMap<>();
        if (isConnected && connectivityMonitorService.isConected()) { // Si hay conexion a internet
            try {
                response = ioTMessagesHandlerService.sendSyncMessage(securityProvider,this.iotHubUri,this.idDevice,message,Integer.parseInt(Utilities.readProperty("milliseconsToTimeout","120000")));
            } catch (Exception e) { // Fallo, paso a local
                response = localMassagesHandlerServiceImp.sendSyncMessage(securityProvider,this.idDevice,message);
            }
        } else {// Si NO hay conexion a internet
            response = localMassagesHandlerServiceImp.sendSyncMessage(securityProvider,this.idDevice,message);
        }
        return response;
    }

    /*
    private Map<String,Object> handleRemoteSynchronousMessage(SecurityProvider securityProvider,String msg, Integer millisecondsToTimeout) {
        AtomicReference<Map<String, Object>> response = new AtomicReference(new HashMap());
        JsonObject jMessage = (new JsonParser()).parse(msg).getAsJsonObject();
        String idSample = jMessage.has("value") ? jMessage.get("value").getAsString() : "NONE";
        final Semaphore mutexSend = new Semaphore(1);
        new AtomicReference("");

        try {
            mutexSend.acquire();
        } catch (InterruptedException var18) {
            throw new RuntimeException(var18);
        }

        Timer timerSend = new Timer(millisecondsToTimeout, (e) -> {
            response.set(this.generateErrorResponse(idDevice, idSample, new TimeoutException("Timeout on sent to IoT Hub"), "Timeout occurred when sending the message to IoTHub"));
            mutexSend.release();
        });
        timerSend.start();

        try {
            this.ioTMessagesHandlerService.sendMessageMQTT(securityProvider, iotHubUri, idDevice, msg, new MessageSentCallback() {
                public void onMessageSent(Message message, IotHubClientException e, Object o) {
                    System.out.println("Message sent!: \n\t" + new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
                    mutexSend.release();
                }
            });
            mutexSend.acquire();
        } catch (Exception var17) {
            response.set(this.generateErrorResponse(idDevice, idSample, var17, "Timeout occurred when sending the message to IoTHub"));
            mutexSend.release();
        }

        Semaphore mutexSemaphore = new Semaphore(1);

        try {
            mutexSemaphore.acquire();
            System.out.println();
            Timer timer = new Timer(millisecondsToTimeout, (e) -> {
                response.set(this.generateErrorResponse(idDevice, idSample, new TimeoutException("Timeout"), "Timeout occurred when is waiting the response"));
                mutexSemaphore.release();
            });
            timer.start();
            this.startCloudMessagesListener(securityProvider, iotHubUri, idDevice, (iotHubDeviceConectionStatus) -> {
                System.out.println("Status: " + iotHubDeviceConectionStatus.getStatus().toString());
                System.out.println("ChangeReason: " + iotHubDeviceConectionStatus.getStatusChangeReason().toString());
            }, (ioTHubMessage) -> {
                System.out.println("Counter: " + ioTHubMessage.getCounter().toString() + ", Message: " + ioTHubMessage.getMessageStr());
                JsonObject jResponse = JsonParser.parseString(ioTHubMessage.getMessageStr()).getAsJsonObject();
                response.set((Map)(new Gson()).fromJson(jResponse, Map.class));
                mutexSemaphore.release();
            });
            mutexSemaphore.acquire();
            this.stopCloudMessagesListener();
        } catch (IOException var14) {
            response.set(this.generateErrorResponse(idDevice, idSample, var14, "IOException occurred when is waiting the response"));
            var14.printStackTrace();
        } catch (InterruptedException var15) {
            response.set(this.generateErrorResponse(idDevice, idSample, var15, "InterruptedException occurred when is waiting the response"));
            var15.printStackTrace();
        } catch (IotHubClientException var16) {
            response.set(this.generateErrorResponse(idDevice, idSample, var16, "IotHubClientException occurred when is waiting the response"));
            var16.printStackTrace();
        }

        return (Map)response.get();
    }
    */

    @Override
    public ProvisioningStatus registerDeviceWithIntermediate(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath) {
        return this.ioTConnectionDeviceService.registerDeviceWithIntermediate(publicDeviceCertificatePath,privateDeviceCertificatePath,publicIntermediateCertificatePath);
    }
}
