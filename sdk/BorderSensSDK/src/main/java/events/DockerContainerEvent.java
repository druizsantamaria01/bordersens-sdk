package events;

import model.ContainerStatus;
import model.DeployContainerStatus;

public interface DockerContainerEvent {

    void onDockerStateChange(boolean isDockerRunning);

    void onDockerContainerIsDeployed(String image,DeployContainerStatus status, ContainerStatus containerStatus);
}
