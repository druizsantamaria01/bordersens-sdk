package model;

import com.spotify.docker.client.messages.Container;

public class ContainerStatus {
    private String id;
    private DataContainerRepository.ImagesContainer image;

    private Container container;
    private ContainerStates state;

    private int restarts;

    public ContainerStatus() {
        this.restarts = 0;
    }

    public ContainerStatus(String id) {
        this.id = id;
        this.restarts = 0;
    }

    public ContainerStatus(String id, DataContainerRepository.ImagesContainer image) {
        this.id = id;
        this.image = image;
        this.restarts = 0;
    }

    public ContainerStatus(String id, DataContainerRepository.ImagesContainer image, Container container) {
        this.id = id;
        this.image = image;
        this.container = container;
        this.state = ContainerStates.getState(container.state());
    }

    public ContainerStatus(String id, DataContainerRepository.ImagesContainer  image, ContainerStates state) {
        this.id = id;
        this.image = image;
        this.state = state;
        this.restarts = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DataContainerRepository.ImagesContainer  getImage() {
        return image;
    }

    public void setImage(DataContainerRepository.ImagesContainer  image) {
        this.image = image;
    }

    public ContainerStates getState() {
        return state;
    }

    public void setState(ContainerStates state) {
        this.state = state;
    }

    public int getRestarts() {
        return restarts;
    }

    public void setRestarts(int restarts) {
        this.restarts = restarts;
    }
}
