package services.impl;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.auth.FixedRegistryAuthSupplier;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import model.ContainerStates;
import model.DataContainerRepository;
import model.DockerRepositories;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import services.DockerService;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DockerServiceImp implements DockerService {

    private static DockerServiceImp instance;
    private DockerClient dockerClient;

    private boolean isDockerRunning = false;

    private Map<DockerRepositories, RegistryAuth> registries;

    public static DockerService getInstance(){
        if (instance == null)
            instance = new DockerServiceImp();
        return instance;
    }

    private DockerServiceImp() {
        this.instance = this;
        this.registries = new HashMap<>();
        try {
            dockerClient = DefaultDockerClient.fromEnv()
                    .connectTimeoutMillis(5000L)
                    .readTimeoutMillis(20000L)
                    .connectionPoolSize(10)
                    .registryAuthSupplier(new FixedRegistryAuthSupplier())
                    .build();
            this.isDockerRunning = checkIfDockerIsRunning();
        } catch (DockerCertificateException e) {
            this.isDockerRunning = false;
            throw new RuntimeException(e);
        }
    }

    @Override
    public DockerClient getDockerClient() {
        return instance.dockerClient;
    }


    public boolean isDockerRunning() {
        return isDockerRunning;
    }

    @Override
    public boolean checkIfDockerIsRunning() {
        try {
            dockerClient.info();
            isDockerRunning = true;
        } catch (DockerException e) {
            isDockerRunning = false;
        } catch (InterruptedException e) {
            isDockerRunning = false;
        }
        return isDockerRunning;
    }

    @Override
    public void doRegistry(DockerRepositories repositories, String serverAddress, String email, String userName, String password) throws IllegalArgumentException {
        RegistryAuth.Builder registryBuilder = RegistryAuth.builder();
        if (serverAddress!=null)
            registryBuilder.serverAddress(serverAddress);
        if (email!=null)
            registryBuilder.email(email);
        if (userName==null)
            throw new IllegalArgumentException("Argument userName is required");
        else
            registryBuilder.username(userName);
        if (password==null)
            throw new IllegalArgumentException("Argument password is required");
        else
            registryBuilder.password(password);
        RegistryAuth registryAuth  = registryBuilder.build();
        this.registries.put(repositories,registryAuth);
    }

    @Override
    public void doRegistry(DataContainerRepository dcr) throws IllegalArgumentException {
        doRegistry(dcr.getRepository(),dcr.getServerAddress(),dcr.getEmail(),dcr.getUser(),dcr.getPassword());
    }

    @Override
    public List<Container> listAllContainers(ContainerStates state) throws DockerException, InterruptedException {
        DockerClient.ListContainersParam params = DockerClient.ListContainersParam.allContainers();
        switch (state) {
            case ALL:
                params = DockerClient.ListContainersParam.allContainers();
                break;
            case RUNNING:
                params = DockerClient.ListContainersParam.withStatusRunning();
                break;
            case CREATED:
                params = DockerClient.ListContainersParam.withStatusCreated();
                break;
            case EXITED:
                params = DockerClient.ListContainersParam.withStatusExited();
                break;
            case PAUSED:
                params = DockerClient.ListContainersParam.withStatusPaused();
                break;
            case RESTARTING:
                params = DockerClient.ListContainersParam.withStatusRestarting();
                break;
        }
        return dockerClient.listContainers(params);
    }

    @Override
    public List<Container> listAllContainersByImageName(String imageName, ContainerStates state) throws DockerException, InterruptedException {
        List<Container> containersFiltered = new ArrayList<>();
        for (Container c: listAllContainers(state)) {
            if (c.image().contains(imageName))
                containersFiltered.add(c);
        }
        return containersFiltered;
    }

    @Override
    public Container getContainersById(String id, ContainerStates state) throws DockerException, InterruptedException {
        for (Container c: listAllContainers(state)) {
            if (c.id().equals(id))
                return c;
        }
        return null;
    }

    @Override
    public List<Image> listAllImages() throws DockerException, InterruptedException {
        return dockerClient.listImages();
    }

    @Override
    public List<Image> listAllImagesByTagAndVersion(String tag,String version) throws DockerException, InterruptedException {
        List<Image> imagesFiltered = new ArrayList<>();
        for (Image i : dockerClient.listImages()) {
            if (i.repoTags()!=null) {
                i.repoTags().forEach(repo -> {
                    String[] r = repo.split(":");
                    if (r[0].contains(tag) && (version == null || r[1].equals(version))) {
                        imagesFiltered.add(i);
                    }
                });
            }
        }
        return imagesFiltered;
    }

    @Override
    public List<RemovedImage> removeImages(String tag, String version) throws DockerException, InterruptedException {
        List<Image> images = listAllImagesByTagAndVersion(tag,version);
        List<RemovedImage> removedImages = new ArrayList<>();
        for (Image i: images) {
            try {
                List<RemovedImage> removed = dockerClient.removeImage(i.id(), true, false);
                removedImages.addAll(removed);
            } catch (Exception e) {

            }
        }
        return removedImages;
    }

    @Override
    public boolean pullImage(DockerRepositories registry, String imageName) throws DockerException, InterruptedException {
        if (registry==null) {
            dockerClient.pull(imageName);
            return true;
        } else if (!this.registries.containsKey(registry)) {
            throw new IllegalArgumentException("Registry " + registry + " is not created, please first call doRegistry method");
        } else {
            RegistryAuth registryAuth = this.registries.get(registry);
            dockerClient.pull(imageName, registryAuth);
            return true;
        }
    }


    @Override
    public String runContainer(String imageName,String containerName, List<String> containerPorts, List<String> hostPorts,List<String> volumes, Map<String,String> env,List<String> cmd, List<String> entryPoint) throws DockerException, InterruptedException {
        if (containerPorts != hostPorts && ( (containerPorts!=null?containerPorts.size():0) != (hostPorts!=null?hostPorts.size():0) )) {
            throw new IllegalArgumentException("Container Ports and Host Ports have to be the same length");
        }
        // Mapping ports
        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        for (int i = 0 ; i < containerPorts.size() ; i++) {
            List<PortBinding> hPorts = new ArrayList<>();
            hPorts.add(PortBinding.of("0.0.0.0",hostPorts.get(i)));
            portBindings.put(String.valueOf(containerPorts.get(i)), hPorts);
        }
        final HostConfig.Builder hostConfigBuilder = HostConfig.builder();
        hostConfigBuilder.portBindings(portBindings);


        if (volumes!=null && volumes.size()>0) {
            final ImmutableList.Builder<String> bindsVols = ImmutableList.builder();
            for (String v : volumes) {
                String[] vData = v.split(":");
                if (vData.length == 2) {
                    Volume vol = instance.createVolumeIfNotExist(vData[0]);
                    bindsVols.add(v);
                }
            }
            hostConfigBuilder.binds(bindsVols.build());
        }
        /*
        final HostConfig hostConfig = HostConfig.builder()
                .portBindings(portBindings).build();
         */
        // Container config

        /*
        final ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        containerConfigBuilder.hostConfig(hostConfig);
        containerConfigBuilder.image(imageName);
        containerConfigBuilder.exposedPorts( containerPorts.toArray(new String[0]) );
        if (volumes!=null && volumes.size()>0) {
            for (String v : volumes) {
                String[] vData = v.split(":");
                if (vData.length == 2) {
                    Volume vol = instance.createVolumeIfNotExist(vData[0]);
                    containerConfigBuilder.addVolume(v);
                }
            }
        }
         */
        final HostConfig hostConfig = hostConfigBuilder.build();
        final ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();
        containerConfigBuilder.hostConfig(hostConfig);
        containerConfigBuilder.image(imageName);
        containerConfigBuilder.exposedPorts( containerPorts.toArray(new String[0]) );
        if (env!=null && env.size()>0) {
            List<String> envs = new ArrayList<>();
            for (Map.Entry<String, String> e: env.entrySet() ) {
                envs.add(e.getKey()+"="+e.getValue());
            }
            containerConfigBuilder.env(envs);
        }
        if (cmd!=null && cmd.size()>0) {
            containerConfigBuilder.cmd(cmd);
        }
        if (entryPoint!=null && entryPoint.size()>0) {
            containerConfigBuilder.entrypoint(entryPoint);
        }
        // containerConfigBuilder.addVolume();
        ContainerConfig containerConfig = containerConfigBuilder.build();

        // Container creation
        final ContainerCreation creation;
        if (containerName!=null) {
             creation = dockerClient.createContainer(containerConfig,containerName);
        } else {
            creation = dockerClient.createContainer(containerConfig);
        }
        final String id = creation.id();

        // Inspect container
        final ContainerInfo info = dockerClient.inspectContainer(id);
        System.out.println(info.toString());
        // Start container
        dockerClient.startContainer(id);
        return id;
    }


    @Override
    public void stopContainer(String idContainer, int seconds) throws DockerException, InterruptedException {
        dockerClient.stopContainer(idContainer,seconds);
    }

    @Override
    public void restartContainer(String idContainer, Integer seconds) throws DockerException, InterruptedException {
        if (seconds!=null)
            dockerClient.restartContainer(idContainer,seconds);
        else
            dockerClient.restartContainer(idContainer);
    }

    @Override
    public void removeContainer(String idContainer) throws DockerException, InterruptedException {
        Container c = getContainersById(idContainer, ContainerStates.RUNNING);
        if (c!=null) {
            dockerClient.stopContainer(c.id(),0);
        }
        dockerClient.removeContainer(idContainer);
    }

    @Override
    public void removeAll(String imageName) throws DockerException, InterruptedException {
        for (Container c : listAllContainersByImageName(imageName,ContainerStates.ALL)) {
            removeContainer(c.id());
        }
        String[] repoTags = imageName.split(":");
        removeImages(repoTags[0],(repoTags.length>1)?repoTags[1]:null);
    }

    @Override
    public void clearContainer(String imageName) throws DockerException, InterruptedException {
        for (Container c : listAllContainersByImageName(imageName,ContainerStates.ALL)) {
            removeContainer(c.id());
        }
    }

    @Override
    public Container forceCleanContainerFromImageName(DockerRepositories registry,String imageName,String containerName, List<String> containerPorts, List<String> hostPorts,List<String> volumes,Map<String,String> env,List<String> cmd, List<String> entryPoint) throws DockerException, InterruptedException {
        removeAll(imageName);
        pullImage(registry,imageName);
        String id = runContainer(imageName,containerName,containerPorts,hostPorts,volumes,env,cmd,entryPoint);
        Container c = getContainersById(id,ContainerStates.ALL);
        return c;
    }

    @Override
    public long getLastUpdateFromImage(DockerRepositories repository, String imageName) throws IOException, ParseException {
        if (repository == null) {
            String[] name = imageName.split(":");
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
            Request request = new Request.Builder()
                    .url("https://hub.docker.com/v2/repositories/library/"+name[0]+"/tags/"+name[1])
                    .build();
            Response response = client.newCall(request).execute();
            JSONObject b = new JSONObject(response.body().string());
            String lastUpdateTime = b.getString("last_updated");
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(lastUpdateTime);
            return d.getTime();
        } else if (repository == DockerRepositories.DOCKERHUB) {
            String[] name = imageName.split(":");
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
            Request request = new Request.Builder()
                    .url("https://hub.docker.com/v2/repositories/"+name[0]+"/tags/")
                    .build();
            Response response = client.newCall(request).execute();
            JSONObject b = new JSONObject(response.body().string());
            if (b.has("results")) {
                JSONArray jResults = b.getJSONArray("results");
                Date d = null;
                for (int i = 0; i < jResults.length(); i++) {
                    JSONObject jResult = jResults.getJSONObject(i);
                    if (name.length>1 && jResult.getString("name").equals(name[1])) {
                        String lastUpdateTime = jResult.getString("last_updated");
                        d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(lastUpdateTime);
                        break;
                    } else if (name.length==1) {
                        if (d == null) {
                            String lastUpdateTime = jResult.getString("last_updated");
                            d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(lastUpdateTime);
                        } else {
                            String lastUpdateTime = jResult.getString("last_updated");
                            Date dAux = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(lastUpdateTime);
                            if (dAux.getTime()>d.getTime())
                                d = dAux;
                        }
                    }
                }
                return d.getTime();
            }
        } else if (repository == DockerRepositories.ACR) {
            RegistryAuth registryAuth = this.registries.get(repository);
            String username = registryAuth.username();
            String password = registryAuth.password();
            String userpass = username + ":" + password;
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
            //RequestBody body = RequestBody.create(mediaType, "");
            String[] repoNameSplit = imageName.split("/");
            String url = "https://"+repoNameSplit[0]+"/acr/v1/"+repoNameSplit[1].split(":")[0];
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", basicAuth)
                    .build();
            Response response = client.newCall(request).execute();
            JSONObject b = new JSONObject(response.body().string());
            if (b.has("lastUpdateTime")) {
                String lastUpdateTime = b.getString("lastUpdateTime");
                Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(lastUpdateTime);
                return d.getTime();
            }
        }
        return -1;
    }

    @Override
    // TODO: Check
    public boolean checkIfImageContainerHasUpdated(DockerRepositories repository, String imageName) throws IOException, ParseException, DockerException, InterruptedException {
        long lastUpdate = instance.getLastUpdateFromImage(repository,imageName);
        if (lastUpdate>-1) {
            String[] splitImageName = imageName.split(":");

            List<Container> containers = listAllContainersByImageName(splitImageName[0].substring(splitImageName[0].lastIndexOf("/")+1),ContainerStates.RUNNING);
            for (Container c : containers) {
                ContainerInfo ii = dockerClient.inspectContainer(c.id());
                long ddd = ii.created().getTime();
                if (ii.created().getTime()<lastUpdate)
                    return true;
            }
        }
        return false;
    }

    @Override
    public Volume createVolume(String volumeName) throws DockerException, InterruptedException {
        final Volume toCreate = Volume.builder()
                .name(volumeName)
                .driver("local")
                .build();
        final Volume created = dockerClient.createVolume(toCreate);
        return created;
    }

    @Override
    public Volume createVolumeIfNotExist(String volumeName) throws DockerException, InterruptedException {
        try {
            Volume v = dockerClient.inspectVolume(volumeName);
            if (v != null)
                return v;
            else {
                final Volume toCreate = Volume.builder()
                        .name(volumeName)
                        .driver("local")
                        .build();
                final Volume created = dockerClient.createVolume(toCreate);
                return created;
            }
        } catch (Exception e) {
            final Volume toCreate = Volume.builder()
                    .name(volumeName)
                    .driver("local")
                    .build();
            final Volume created = dockerClient.createVolume(toCreate);
            return created;
        }
    }

    @Override
    public String buildImageFromDockerfile(String path, String name) throws DockerException, IOException, InterruptedException {
        final AtomicReference<String> imageIdFromMessage = new AtomicReference<>();
        final String returnedImageId = dockerClient.build(Paths.get(path),"test",name, new ProgressHandler() {
            @Override
            public void progress(ProgressMessage message) throws DockerException {
                final String imageId = message.buildImageId();
                if (imageId != null) {
                    imageIdFromMessage.set(imageId);
                }
            }
        });
        return returnedImageId;
    }

}

/*
.filter((i)-> {
             i.repoTags().stream().filter(r -> {
                 String[] repo= tag.split(":");
                 return repo[0].toLowerCase().contains(tag) && (version == null || repo[1].toLowerCase().equals(version));
             });
         });
 */