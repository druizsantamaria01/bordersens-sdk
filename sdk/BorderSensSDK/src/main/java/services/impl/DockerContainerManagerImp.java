package services.impl;

import com.google.gson.JsonObject;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import events.DockerContainerEvent;
import events.InternetStateEvent;
import model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.exception.ExceptionUtils;
import sdkutils.Utilities;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerContainerManagerImp {

    private static DockerContainerManagerImp instance;

    private DockerServiceImp dockerServiceImp;

    private ConnectivityMonitorService connectivityMonitorService;

    private ScheduledExecutorService scheduledExecutorServiceCheckDockerStatus;

    public List<DataContainerRepository> containerRepositories;

    private List<DockerContainerEvent> listeners;

    private boolean isConnected = false;

    private boolean isDockerRunning = false;

    private boolean isContainerDeployed = false;

    private Map<String, ContainerStatus> containerStates;

    private List<DataContainerRepository.ImagesContainer> pendingContainers;

    public static boolean isDoingInference = false;

    private final static Logger logger = Logger.getLogger("DockerContainerManagerImp");

    public static DockerContainerManagerImp getInstance() {
        if (instance==null) {
            instance = new DockerContainerManagerImp();
        }
        return instance;
    }

    public static DockerContainerManagerImp getInstance(DockerContainerEvent listener) {
        if (instance==null) {
            instance = new DockerContainerManagerImp();
        }
        instance.listeners.add(listener);
        return instance;
    }

    public void addListener(DockerContainerEvent listener) {
        listeners.add(listener);
    }

    public void removeListener(DockerContainerEvent listener) {
        listeners.remove(listener);
    }

    private DockerContainerManagerImp() {
        this.listeners = new ArrayList<>();
        this.dockerServiceImp = (DockerServiceImp) DockerServiceImp.getInstance();
        this.connectivityMonitorService = ConnectivityMonitorService.getInstance(new InternetStateEvent() {
            @Override
            public void onChangeConnectionState(boolean isConn) {
                isConnected = isConn;
                if (!isContainerDeployed) {
                    initialize();
                }
            }
        });
        this.isConnected = connectivityMonitorService.isConected();
        this.containerRepositories = new ArrayList<>();
        for (JsonObject jItem : Utilities.readComplexProperty("docker.repository")) {
            containerRepositories.add(new DataContainerRepository(jItem));
        }
        this.isDockerRunning = dockerServiceImp.isDockerRunning();

        containerStates = new HashMap<>();
        pendingContainers = DataContainerRepository.getSortedImages(this.containerRepositories);
    }


    public void initialize() {
        // Start Containers
        if (isDockerRunning) {
            for (DataContainerRepository.ImagesContainer image : new ArrayList<>(pendingContainers)) {
                String imageName = image.getImageName() + ":" + image.getImageVersion();
                try {
                    Container container;
                    dockerServiceImp.doRegistry(image.getDataContainerRepository());
                    if (isConnected) {
                        try {
                            if (dockerServiceImp.checkIfImageContainerHasUpdated(image.getDataContainerRepository().getRepository(),image.getImageName())) {
                                dockerServiceImp.pullImage(image.getDataContainerRepository().getRepository(), imageName);
                                container = dockerServiceImp.forceCleanContainerFromImageName(image.getDataContainerRepository().getRepository(), imageName, image.getContainerName(), image.getContainerPorts(), image.getHostPorts(),image.getVolumes(), image.getEnvironment(), image.getCmd(), image.getEntrypoint());
                            } else {
                                dockerServiceImp.clearContainer(imageName);
                                String containerId = dockerServiceImp.runContainer( imageName,image.getContainerName(), image.getContainerPorts(), image.getHostPorts(), image.getVolumes(), image.getEnvironment(), image.getCmd(), image.getEntrypoint());
                                container = dockerServiceImp.getContainersById(containerId,ContainerStates.RUNNING);
                            }
                        } catch (IOException e) {
                            dockerServiceImp.clearContainer(imageName);
                            String containerId = dockerServiceImp.runContainer( imageName,image.getContainerName(), image.getContainerPorts(), image.getHostPorts(), image.getVolumes(), image.getEnvironment(), image.getCmd(), image.getEntrypoint());
                            container = dockerServiceImp.getContainersById(containerId,ContainerStates.RUNNING);
                        } catch (ParseException e) {
                            dockerServiceImp.clearContainer(imageName);
                            String containerId = dockerServiceImp.runContainer( imageName,image.getContainerName(), image.getContainerPorts(), image.getHostPorts(), image.getVolumes(), image.getEnvironment(), image.getCmd(), image.getEntrypoint());
                            container = dockerServiceImp.getContainersById(containerId,ContainerStates.RUNNING);
                        }
                    } else {
                        dockerServiceImp.clearContainer(imageName);
                        String containerId = dockerServiceImp.runContainer( imageName,image.getContainerName(), image.getContainerPorts(), image.getHostPorts(), image.getVolumes(), image.getEnvironment(), image.getCmd(), image.getEntrypoint());
                        container = dockerServiceImp.getContainersById(containerId,ContainerStates.RUNNING);
                    }
                    ContainerStatus containerStatus = new ContainerStatus(container.id(), image, container);
                    containerStates.put(container.id(), containerStatus);
                    logger.log(Level.INFO, String.format("Deployed container %s with ID: %s", imageName, container.id()));
                    isContainerDeployed = true;
                    pendingContainers.remove(image);
                    for (DockerContainerEvent l : listeners) {
                        l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.DEPLOYED, containerStatus);
                    }
                } catch (DockerException e) {
                    isContainerDeployed = false;
                    logger.log(Level.SEVERE, e.getMessage());
                    logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                    for (DockerContainerEvent l : listeners) {
                        l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                    }
                } catch (InterruptedException e) {
                    isContainerDeployed = false;
                    logger.log(Level.SEVERE, e.getMessage());
                    logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                    for (DockerContainerEvent l : listeners) {
                        l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                    }
                }
            }
        }
        // Schedule Check Docker
        scheduledExecutorServiceCheckDockerStatus = Executors.newScheduledThreadPool(1);
        scheduledExecutorServiceCheckDockerStatus.scheduleAtFixedRate(doCheckContainerStatus(this), Integer.parseInt(Utilities.readProperty("docker.checkContainers.initialDelay","600")), Integer.parseInt(Utilities.readProperty("docker.checkContainers.period","600")), TimeUnit.SECONDS);
    }



    public boolean checkStatus(DataContainerRepository.ImagesContainer image) {
        if (image.getHealthEndpoint()!=null && !image.getHealthEndpoint().equals("")) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(image.getHealthEndpoint()).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.code()!=200) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    public boolean checkStatus() {
        for (DataContainerRepository dcr : this.containerRepositories) {
            for (DataContainerRepository.ImagesContainer image : dcr.getImages().values()) {
                if (image.getHealthEndpoint()!=null && !image.getHealthEndpoint().equals("")) {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(image.getHealthEndpoint()).build();
                    try {
                        Response response = client.newCall(request).execute();
                        if (response.code()!=200) {
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

    private static Runnable doCheckContainerStatus(DockerContainerManagerImp parent) {
        return () ->{
            if (!isDoingInference) {
                logger.log(Level.INFO, "Starting check on deployed containers");
                boolean isDockerRunning = parent.dockerServiceImp.checkIfDockerIsRunning();
                if (isDockerRunning != parent.isDockerRunning) {
                    if (parent.listeners != null) {
                        for (DockerContainerEvent l : parent.listeners) {
                            l.onDockerStateChange(isDockerRunning);
                        }
                    }
                }
                parent.isDockerRunning = isDockerRunning;
                if (parent.isDockerRunning) {
                    if (!parent.isContainerDeployed && parent.pendingContainers.size() > 0) { // Inicializa los contendores en caso de que no estuviesen desplegados o hubiese contenedores que desplegar
                        parent.initialize();
                    }
                    // Checkea el estado de los contenedores desplegados
                    logger.log(Level.INFO, "Starting check the state of the deployed containers");
                    List<DataContainerRepository.ImagesContainer> desiredImages = DataContainerRepository.getSortedImages(parent.containerRepositories);
                    // Comprueba que estén corriendo y si no lo están los levanta
                    for (DataContainerRepository.ImagesContainer imageDesired : desiredImages) {
                        // Comprueba que estén corriendo y si no lo están los levanta
                        String imageName = imageDesired.getImageName() + ":" + imageDesired.getImageVersion();
                        try {
                            List<Container> runningContainers = parent.dockerServiceImp.listAllContainersByImageName(imageName, ContainerStates.RUNNING);
                            if (runningContainers == null || runningContainers.size() == 0) {
                                logger.log(Level.INFO, String.format("No containers deployed for image %s", imageName));
                                parent.dockerServiceImp.forceCleanContainerFromImageName(imageDesired.getDataContainerRepository().getRepository(), imageName, imageDesired.getContainerName(), imageDesired.getContainerPorts(), imageDesired.getHostPorts(), imageDesired.getVolumes(), imageDesired.getEnvironment(), imageDesired.getCmd(), imageDesired.getEntrypoint());
                                Container containerDeployed;// = parent.dockerServiceImp.forceCleanContainerFromImageName(imageDesired.getDataContainerRepository().getRepository(), imageName, imageDesired.getContainerPorts(), imageDesired.getHostPorts(), imageDesired.getEnvironment(), imageDesired.getCmd(), imageDesired.getEntrypoint());
                                if (parent.isConnected) {
                                    parent.dockerServiceImp.doRegistry(imageDesired.getDataContainerRepository());
                                    parent.dockerServiceImp.pullImage(imageDesired.getDataContainerRepository().getRepository(), imageName);
                                    containerDeployed = parent.dockerServiceImp.forceCleanContainerFromImageName(imageDesired.getDataContainerRepository().getRepository(), imageName, imageDesired.getContainerName(), imageDesired.getContainerPorts(), imageDesired.getHostPorts(), imageDesired.getVolumes(), imageDesired.getEnvironment(), imageDesired.getCmd(), imageDesired.getEntrypoint());
                                } else {
                                    parent.dockerServiceImp.clearContainer(imageName);
                                    String containerId = parent.dockerServiceImp.runContainer(imageName, imageDesired.getContainerName(), imageDesired.getContainerPorts(), imageDesired.getHostPorts(), imageDesired.getVolumes(), imageDesired.getEnvironment(), imageDesired.getCmd(), imageDesired.getEntrypoint());
                                    containerDeployed = parent.dockerServiceImp.getContainersById(containerId, ContainerStates.RUNNING);
                                }

                                ContainerStatus containerStatus = new ContainerStatus(containerDeployed.id(), imageDesired, containerDeployed);
                                parent.containerStates.put(containerDeployed.id(), containerStatus);
                                logger.log(Level.INFO, String.format("Deployed container with id %s for image %s", containerStatus.getId(), imageName));
                                for (DockerContainerEvent l : parent.listeners) {
                                    l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.DEPLOYED, containerStatus);
                                }
                            } else {
                                for (Container c : runningContainers) {
                                    logger.log(Level.INFO, String.format("Container %s checked with image name: %s with current status: %s. No anomalies are detected, therefore no actions are applied", c.id(), imageName, c.state()));
                                }
                            }
                        } catch (DockerException e) {
                            throw new RuntimeException(e);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    // Comprueba si hay nuevas versiones de las imágenes
                    if (parent.isConnected) {
                        logger.log(Level.INFO, "Starting check new images versions of the deployed containers");
                        for (DataContainerRepository.ImagesContainer imageDesired : desiredImages) {
                            // Comprueba que estén corriendo y si no lo están los levanta
                            String imageName = imageDesired.getImageName() + ":" + imageDesired.getImageVersion();
                            try {
                                boolean imagenChange = parent.dockerServiceImp.checkIfImageContainerHasUpdated(imageDesired.getDataContainerRepository().getRepository(), imageName);
                                List<Container> runningContainers = parent.dockerServiceImp.listAllContainersByImageName(imageName, ContainerStates.RUNNING);
                                if (imagenChange) {
                                    logger.log(Level.INFO, String.format("Image change detected in image: %s", imageName));
                                    for (Container c : runningContainers) {
                                        parent.dockerServiceImp.stopContainer(c.id(), 0);
                                        parent.dockerServiceImp.removeContainer(c.id());
                                        parent.dockerServiceImp.pullImage(imageDesired.getDataContainerRepository().getRepository(), imageName);
                                        Container containerDeployed = parent.dockerServiceImp.forceCleanContainerFromImageName(imageDesired.getDataContainerRepository().getRepository(), imageName, imageDesired.getContainerName(), imageDesired.getContainerPorts(), imageDesired.getHostPorts(), imageDesired.getVolumes(), imageDesired.getEnvironment(), imageDesired.getCmd(), imageDesired.getEntrypoint());
                                        ContainerStatus containerStatus = new ContainerStatus(containerDeployed.id(), imageDesired, containerDeployed);
                                        parent.containerStates.put(containerDeployed.id(), containerStatus);
                                        logger.log(Level.INFO, String.format("Deployed container with id %s for image %s (new version)", containerStatus.getId(), imageName));
                                        for (DockerContainerEvent l : parent.listeners) {
                                            l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.DEPLOYED, containerStatus);
                                        }
                                    }
                                } else {
                                    for (Container c : runningContainers) {
                                        logger.log(Level.INFO, String.format("Container %s checked with image name: %s with current status: %s. No versión changes are detected, therefore no actions are applied", c.id(), imageName, c.state()));
                                    }
                                }
                            } catch (IOException e) {
                                parent.isContainerDeployed = false;
                                logger.log(Level.SEVERE, e.getMessage());
                                logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                                for (DockerContainerEvent l : parent.listeners) {
                                    l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                                }
                            } catch (ParseException e) {
                                parent.isContainerDeployed = false;
                                logger.log(Level.SEVERE, e.getMessage());
                                logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                                for (DockerContainerEvent l : parent.listeners) {
                                    l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                                }
                            } catch (DockerException e) {
                                parent.isContainerDeployed = false;
                                logger.log(Level.SEVERE, e.getMessage());
                                logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                                for (DockerContainerEvent l : parent.listeners) {
                                    l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                                }
                            } catch (InterruptedException e) {
                                parent.isContainerDeployed = false;
                                logger.log(Level.SEVERE, e.getMessage());
                                logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e));
                                for (DockerContainerEvent l : parent.listeners) {
                                    l.onDockerContainerIsDeployed(imageName, DeployContainerStatus.FAIL, null);
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    public boolean isDockerManagerReady() {
        return isDockerRunning && isContainerDeployed;
    }

}

/*
 Runnable taskSynchronization = doSynchronizationData(this);
        scheduledExecutorService.scheduleAtFixedRate(taskSynchronization, seconds, seconds, TimeUnit.SECONDS);
 */