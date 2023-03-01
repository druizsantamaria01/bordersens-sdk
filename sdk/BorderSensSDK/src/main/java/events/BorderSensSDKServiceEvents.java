package events;

import com.google.gson.JsonObject;
import model.ContainerStatus;
import model.DeployContainerStatus;

import java.util.logging.Level;

public interface BorderSensSDKServiceEvents {

    void onDockerStateChange(boolean isDockerRunning);
    void onDockerContainerIsDeployed(String image, DeployContainerStatus status, ContainerStatus containerStatus);
    void OnDataSynchronizedIsDone(JsonObject jReponse);

    void OnBorderSensSDKServiceIsInitialized(boolean isDockerInitialized,boolean isDataSynchronizedInitialized);
}
