package services.impl;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.ImageSearchResult;
import com.spotify.docker.client.messages.Volume;
import model.ContainerStates;
import model.DockerRepositories;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

class DockerServiceImpTest {

    @Test
    void getInstance()
    {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        Assertions.assertNotNull(instance);
    }

    @Test
    void OtherTest() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        DockerClient dc = instance.getDockerClient();
        try {
            List<ImageSearchResult> searchResult = dc.searchImages("mongo:latest");
            System.out.println();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


    @Test
    void doRegistry() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.doRegistry(DockerRepositories.DOCKERHUB,null,"daniel.ruiz.eng@gmail.com","dockerfootprint","iNFIERN0");
            Assertions.assertTrue(true);
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }


    @Test
    void listAllContainers() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            List<Container> containers = instance.listAllContainers(ContainerStates.RUNNING);
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void listAllContainersByImageName() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            List<Container> containers = instance.listAllContainersByImageName("postgres",ContainerStates.ALL);
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void listAllImages() {
        try {
            List<Image> images = DockerServiceImp.getInstance().listAllImages();
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void listAllImagesByTagAndVersion() {
        try {
            List<Image> images = DockerServiceImp.getInstance().listAllImagesByTagAndVersion("nginx","latesto");
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }



    @Test
    void pullImage() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.pullImage(null,"mongo:latest");
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void pullImageDockerHub() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.doRegistry(DockerRepositories.DOCKERHUB,null,"daniel.ruiz.eng@gmail.com","dockerfootprint","iNFIERN0");
            instance.pullImage(DockerRepositories.DOCKERHUB,"dockerfootprint/k-demoweb:latest");
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void AzureContainerRegistry() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.doRegistry(DockerRepositories.ACR,null,"azure-bi@izertis.com","bscontainerregistry","UU8rWYLn0wWzfhHpY3wGlHj4M96h78NMiuWBUQx6K9+ACRD2qIun");
            instance.pullImage(DockerRepositories.ACR,"bscontainerregistry.azurecr.io/mongo:latest");
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void runContainer() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {

            instance.pullImage(null,"mongo:latest");

            List<Container> containers = instance.listAllContainersByImageName("mongo:latest",ContainerStates.ALL);
            for (Container c : containers) {
                instance.removeContainer(c.id());
            }
            String id = instance.runContainer("mongo:latest",null, Arrays.asList("27017"),Arrays.asList("27017"),null, new HashMap<String, String>() {{put("MONGO_INITDB_ROOT_USERNAME", "admin");put("MONGO_INITDB_ROOT_PASSWORD", "b0rd3rs3ns");}},null,null);
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void stopContainer() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            List<Container> containers = instance.listAllContainersByImageName("mongo:latest", ContainerStates.RUNNING);
            if (containers.size() == 0) {
                String id = instance.runContainer("mongo:latest",null, Arrays.asList("27017"),Arrays.asList("27017"),null, new HashMap<String, String>() {{put("MONGO_INITDB_ROOT_USERNAME", "admin");put("MONGO_INITDB_ROOT_PASSWORD", "b0rd3rs3ns");}},null,null);
                instance.stopContainer(id,0);
            } else {
                String id = containers.get(0).id();
                instance.stopContainer(id,0);
            }
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void restartContainer() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            List<Container> containers = instance.listAllContainersByImageName("mongo:latest", ContainerStates.RUNNING);
            if (containers.size() == 0) {
                instance.pullImage(null,"mongo:latest");
                String id = instance.runContainer("mongo:latest", null,Arrays.asList("27017"),Arrays.asList("27017"), null,new HashMap<String, String>() {{put("MONGO_INITDB_ROOT_USERNAME", "admin");put("MONGO_INITDB_ROOT_PASSWORD", "b0rd3rs3ns");}},null,null);
                instance.restartContainer(id,0);
            } else {
                String id = containers.get(0).id();
                instance.restartContainer(id,0);
            }
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void removeContainer() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            List<Container> containers = instance.listAllContainersByImageName("mongo:latest", ContainerStates.ALL);
            if (containers.size() == 0) {
                String id = instance.runContainer("mongo:latest", null, Arrays.asList("27017"),Arrays.asList("27017"), null,new HashMap<String, String>() {{put("MONGO_INITDB_ROOT_USERNAME", "admin");put("MONGO_INITDB_ROOT_PASSWORD", "b0rd3rs3ns");}},null,null);
                instance.removeContainer(id);
            } else {
                for (Container c : containers) {
                    String id = c.id();
                    instance.removeContainer(id);
                }
            }
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void removeAll() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.removeAll("mongo:latest");
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(true);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(true);
            throw new RuntimeException(e);
        }
    }

    @Test
    void forceCleanContainerFromImageName() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.doRegistry(DockerRepositories.ACR,null,"azure-bi@izertis.com","bscontainerregistry","UU8rWYLn0wWzfhHpY3wGlHj4M96h78NMiuWBUQx6K9+ACRD2qIun");
            Container container = instance.forceCleanContainerFromImageName(DockerRepositories.ACR,"bscontainerregistry.azurecr.io/mongo:latest", null, Arrays.asList("27017"),Arrays.asList("27017"), null, new HashMap<String, String>() {{put("MONGO_INITDB_ROOT_USERNAME", "admin");put("MONGO_INITDB_ROOT_PASSWORD", "b0rd3rs3ns");}},null,null);
            Assertions.assertTrue(true);
        } catch (DockerException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Assertions.assertTrue(false);
            throw new RuntimeException(e);
        }
    }

    @Test
    void getLastUpdateFromImage() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        instance.doRegistry(DockerRepositories.ACR,null,"azure-bi@izertis.com","bscontainerregistry","UU8rWYLn0wWzfhHpY3wGlHj4M96h78NMiuWBUQx6K9+ACRD2qIun");
        instance.doRegistry(DockerRepositories.DOCKERHUB,null,"daniel.ruiz.eng@gmail.com","dockerfootprint","iNFIERN0");
        try {
            long timeACR = instance.getLastUpdateFromImage(DockerRepositories.ACR,"mongo:latest");
            long timeDOCKERHUB = instance.getLastUpdateFromImage(DockerRepositories.DOCKERHUB,"dockerfootprint/poc4-backend-api");
            long timeLIBRARY = instance.getLastUpdateFromImage(null,"mongo:latest");
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void checkIfImageContainerHasUpdated() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            instance.doRegistry(DockerRepositories.ACR,null,"azure-bi@izertis.com","bscontainerregistry","UU8rWYLn0wWzfhHpY3wGlHj4M96h78NMiuWBUQx6K9+ACRD2qIun");
            boolean isUpdated = instance.checkIfImageContainerHasUpdated(DockerRepositories.ACR,"bscontainerregistry.azurecr.io/bordersens-etl-flask:latest");
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (DockerException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void buildImageFromDockerfile() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();


        try {
            String imageID = instance.buildImageFromDockerfile("c:/Users/druiz/repositorios/BorderSens/bordersens-etl-flask/Dockerfile","bordersens-etl-flask-local");
            System.out.println();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createVolume() {
        DockerServiceImp instance = (DockerServiceImp) DockerServiceImp.getInstance();
        try {
            Volume v = instance.createVolume("volumen-test");
            System.out.println();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}