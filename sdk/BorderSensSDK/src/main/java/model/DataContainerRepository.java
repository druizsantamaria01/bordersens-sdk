package model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import sdkutils.Utilities;

import java.util.*;
import java.util.stream.Collectors;

public class DataContainerRepository {

    private DockerRepositories repository;
    private String serverAddress;
    private String email;
    private String user;
    private String password;

    private Map<String,ImagesContainer> images;




    public DataContainerRepository(JsonObject jData) {
        images = new HashMap<>();
        if (jData.has("name")) {
            switch (jData.get("name").getAsString()) {
                case "DOCKERHUB":
                    this.repository = DockerRepositories.DOCKERHUB;
                    break;
                case "ACR":
                    this.repository = DockerRepositories.ACR;
                    break;
            }
        }
        if (jData.has("serverAddress"))
            this.serverAddress = jData.get("serverAddress").getAsString();
        if (jData.has("email"))
            this.email = jData.get("email").getAsString();
        if (jData.has("userName"))
            this.user = jData.get("userName").getAsString();
        if (jData.has("password"))
            this.password = jData.get("password").getAsString();
        if (jData.has("index")) {
            int index = Integer.parseInt(jData.get("index").getAsString());
            List<JsonObject> jDataImages = Utilities.readComplexProperty("docker.repository."+index+".image");
            if (jDataImages!=null) {
                for (JsonElement jeDataImage : jDataImages) {
                    ImagesContainer ic = new ImagesContainer(this,jeDataImage.getAsJsonObject());
                    this.images.put(ic.imageName,ic);
                }

            }
        }
    }

    public DockerRepositories getRepository() {
        return repository;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getEmail() {
        return email;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public Map<String, ImagesContainer> getImages() {
        return images;
    }

    public static List<ImagesContainer> getSortedImages(List<DataContainerRepository> containerRepositories) {
        List<ImagesContainer> imagesSorted = new ArrayList<>();
        List<ImagesContainer> pendingImages = new ArrayList<>();
        for (DataContainerRepository dr : containerRepositories) {
            pendingImages.addAll(dr.getImages().values());
        }
        boolean isFinish = false;
        while (!pendingImages.isEmpty() && !isFinish) {
            int changeNumbers = 0;
            for (ImagesContainer i: new ArrayList<>(pendingImages)) {
                if (i.dependOn == null || i.dependOn.size() == 0) {
                    imagesSorted.add(i);
                    pendingImages.remove(i);
                    changeNumbers += 1;
                } else {
                    List<ImagesContainer> dependencies = imagesSorted.stream().filter(is -> i.dependOn.contains(is.imageName)).collect(Collectors.toList());
                    if (dependencies.size() == i.dependOn.size()) {
                        imagesSorted.add(i);
                        pendingImages.remove(i);
                        changeNumbers += 1;
                    }
                }
            }
            if (changeNumbers == 0) {
                imagesSorted.addAll(pendingImages);
                isFinish = true;
            }
        }
        return imagesSorted;
    }

    public class ImagesContainer {

        private String containerName;
        private String imageName;
        private String imageVersion;
        private List<String> containerPorts;
        private List<String> hostPorts;

        private List<String> volumes;
        private Map<String,String> environment;
        private List<String> cmd;
        private List<String> entrypoint;

        private String healthEndpoint;

        private List<String> dependOn;

        private DataContainerRepository dataContainerRepository;

        public ImagesContainer(DataContainerRepository dataContainerRepository,JsonObject jData) {
            this.dataContainerRepository = dataContainerRepository;
            if (jData.has("containerName"))
                this.containerName = jData.get("containerName").getAsString();
            if (jData.has("imageName"))
                this.imageName = jData.get("imageName").getAsString();
            if (jData.has("imageVersion"))
                this.imageVersion = jData.get("imageVersion").getAsString();

            if (jData.has("portsContainer")) {
                this.containerPorts =new ArrayList<>();
                for (String port : jData.get("portsContainer").getAsString().split(",")) {
                    this.containerPorts.add(port.trim());
                }
            }
            if (jData.has("portsHost")) {
                this.hostPorts =new ArrayList<>();
                for (String port : jData.get("portsHost").getAsString().split(",")) {
                    this.hostPorts.add(port.trim());
                }
            }

            if (jData.has("volumes")) {
                this.volumes =new ArrayList<>();
                for (String item : jData.get("volumes").getAsString().split(",")) {
                    this.volumes.add(item.trim());
                }
            }

            if (jData.has("environment")) {
                this.environment =new HashMap<>();
                for (String env : jData.get("environment").getAsString().split(",")) {
                    String[] envChunk = env.split(":");
                    if (envChunk.length >= 2) {
                        this.environment.put(envChunk[0],String.join(":", Arrays.asList(envChunk).subList(1,envChunk.length)));
                    } else if (envChunk.length > 2) {
                        this.environment.put(envChunk[0],String.join("", Arrays.asList(envChunk).subList(1,envChunk.length)));
                    }
                }
            }

            if (jData.has("cmd")) {
                this.cmd =new ArrayList<>();
                for (String item : jData.get("cmd").getAsString().split(",")) {
                    this.cmd.add(item.trim());
                }
            }

            if (jData.has("entrypoint")) {
                this.entrypoint =new ArrayList<>();
                for (String item : jData.get("entrypoint").getAsString().split(",")) {
                    this.entrypoint.add(item.trim());
                }
            }

            if (jData.has("healthEndpoint")) {
                this.healthEndpoint = jData.get("healthEndpoint").getAsString();
            }

            if (jData.has("dependOn")) {
                this.dependOn =new ArrayList<>();
                for (String item : jData.get("dependOn").getAsString().split(",")) {
                    this.dependOn.add(item.trim());
                }
            }
        }

        public String getImageName() {
            return imageName;
        }

        public String getImageVersion() {
            return imageVersion;
        }

        public List<String> getContainerPorts() {
            return containerPorts;
        }

        public List<String> getHostPorts() {
            return hostPorts;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public List<String> getCmd() {
            return cmd;
        }

        public List<String> getEntrypoint() {
            return entrypoint;
        }

        public String getHealthEndpoint() {
            return healthEndpoint;
        }

        public List<String> getDependOn() {
            return dependOn;
        }

        public DataContainerRepository getDataContainerRepository() {
            return dataContainerRepository;
        }

        public String getContainerName() {
            return containerName;
        }

        public List<String> getVolumes() {
            return volumes;
        }
    }


    public class ContainerStatus {
        private String id;
        private String image;
        private ContainerStates state;

        private int restarts;

        public ContainerStatus() {
            this.restarts = 0;
        }

        public ContainerStatus(String id, String image) {
            this.id = id;
            this.image = image;
            this.restarts = 0;
        }

        public ContainerStatus(String id, String image, ContainerStates state) {
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

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
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
}

