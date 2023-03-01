package services.impl;

import events.DockerContainerEvent;
import model.ContainerStatus;
import model.DeployContainerStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DockerContainerManagerImpTest {

    DockerContainerManagerImp dcm = DockerContainerManagerImp.getInstance();

    @Test
    void getInstance() throws InterruptedException {
        dcm.addListener(new DockerContainerEvent() {
            @Override
            public void onDockerStateChange(boolean isDockerRunning) {
                System.out.println("onDockerStateChange: " +isDockerRunning);
            }

            @Override
            public void onDockerContainerIsDeployed(String image, DeployContainerStatus status, ContainerStatus containerStatus) {
                System.out.println("On image: " +image +" status: "+status);
            }
        });
        dcm.initialize();
        //Thread.sleep(150000);
        System.out.println();
    }
}