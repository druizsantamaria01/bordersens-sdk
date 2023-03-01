package services;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.RemovedImage;
import com.spotify.docker.client.messages.Volume;
import model.ContainerStates;
import model.DataContainerRepository;
import model.DockerRepositories;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public interface DockerService {

    DockerClient getDockerClient();
    void doRegistry(DockerRepositories repositories, String serverAddress, String email, String userName, String password) throws IllegalArgumentException;

    void doRegistry(DataContainerRepository dcr) throws IllegalArgumentException;

    List<Container> listAllContainers(ContainerStates state) throws DockerException, InterruptedException;

    List<Container> listAllContainersByImageName(String imageName, ContainerStates state) throws DockerException, InterruptedException;

    Container getContainersById(String id, ContainerStates state) throws DockerException, InterruptedException;
    List<Image> listAllImages() throws DockerException, InterruptedException;
    List<Image> listAllImagesByTagAndVersion(String tag,String version) throws DockerException, InterruptedException;

    List<RemovedImage> removeImages(String tag, String version) throws DockerException, InterruptedException;
    boolean pullImage(DockerRepositories registry, String imageName) throws DockerException, InterruptedException;
    String runContainer(String imageName, String containerName,List<String> containerPorts, List<String> hostPorts,List<String> volumes, Map<String,String> env, List<String> cmd, List<String> entryPoint) throws DockerException, InterruptedException;

    void stopContainer(String idContainer, int seconds) throws DockerException, InterruptedException;

    void restartContainer(String idContainer, Integer seconds) throws DockerException, InterruptedException;

    void removeContainer(String idContainer) throws DockerException, InterruptedException;

    void removeAll(String imageName) throws DockerException, InterruptedException;

    void clearContainer(String imageName) throws DockerException, InterruptedException;

    Container forceCleanContainerFromImageName(DockerRepositories registry, String imageName,String containerName, List<String> containerPorts, List<String> hostPorts,List<String> volumes, Map<String,String> env, List<String> cmd, List<String> entryPoint) throws DockerException, InterruptedException;

    long getLastUpdateFromImage(DockerRepositories repository, String imageName) throws IOException, ParseException;

    boolean checkIfImageContainerHasUpdated(DockerRepositories repository, String imageName) throws IOException, ParseException, DockerException, InterruptedException;

    boolean checkIfDockerIsRunning();

    Volume createVolume(String volumeName) throws DockerException, InterruptedException;

    Volume createVolumeIfNotExist(String volumeName) throws DockerException, InterruptedException;

    String buildImageFromDockerfile(String path, String name) throws DockerException, IOException, InterruptedException;
}
