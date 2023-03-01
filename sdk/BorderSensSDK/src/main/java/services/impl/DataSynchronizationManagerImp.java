package services.impl;

import com.azure.cosmos.implementation.guava25.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import events.InternetStateEvent;
import events.SynchronizeDataEvent;
import model.DataRepository;
import org.skyscreamer.jsonassert.JSONCompareMode;
import services.DataSynchronizationManager;
import sdkutils.Utilities;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DataSynchronizationManagerImp implements DataSynchronizationManager {

    private static DataSynchronizationManagerImp instance;
    private MongoDBServiceImp mongo;
    private CosmosDBServiceImp cosmos;

    private String cosmosDatabase;

    private List<DataRepository> mongoRepositories;

    private List<DataRepository> cosmosRepositories;

    private boolean isSynchronized;

    private boolean isConnected;

    private String idDevice;

    private ConnectivityMonitorService connectivityMonitorService;

    private List<SynchronizeDataEvent> listeners;

    public static boolean isDoingInference = false;

    ScheduledExecutorService scheduledExecutorService;
    private final static Logger logger = Logger.getLogger("DataSynchronizationManagerImp");

    public static DataSynchronizationManagerImp getInstance(String idDevice) {
        if (instance == null) {
            instance = new DataSynchronizationManagerImp(idDevice);
        }
        return instance;
    }

    public static DataSynchronizationManagerImp getInstance(String idDevice,SynchronizeDataEvent listener) {
        if (instance == null) {
            instance = new DataSynchronizationManagerImp(idDevice);
        }
        instance.listeners.add(listener);
        return instance;
    }

    public DataSynchronizationManagerImp(String idDevice) {
        this.idDevice  = idDevice;
        this.isSynchronized = true;
        this.listeners = new ArrayList<>();
        this.mongo = MongoDBServiceImp.getInstance();
        this.cosmos = CosmosDBServiceImp.getInstance();
        this.cosmosDatabase = Utilities.readProperty("cosmos.database", "BorderSens");
        this.mongoRepositories = new ArrayList<>();
        this.cosmosRepositories = new ArrayList<>();
        for (JsonObject jItem : Utilities.readComplexProperty("mongo.collections")) {
            mongoRepositories.add(new DataRepository(jItem));
        }

        for (JsonObject jItem : Utilities.readComplexProperty("cosmos.collections")) {
            cosmosRepositories.add(new DataRepository(jItem));
        }

        this.connectivityMonitorService = ConnectivityMonitorService.getInstance(new InternetStateEvent() {
            @Override
            public void onChangeConnectionState(boolean isCon) {
                isConnected = isCon;
                if (isConnected && !isSynchronized) {
                    listeners.add(new SynchronizeDataEvent() {
                        @Override
                        public void OnDataSynchronizedIsDone(JsonObject jReponse) {
                            isSynchronized = true;
                        }
                    });
                    synchronizeOnce();
                }
            }
        });
        this.isConnected = connectivityMonitorService.isConected();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);

    }

    public void startSynchronization(int seconds){
        Runnable taskSynchronization = doSynchronizationData(this);
        scheduledExecutorService.scheduleAtFixedRate(taskSynchronization, seconds, seconds, TimeUnit.SECONDS);
    }

    public void startSynchronization(int secondsToStart, int secondsPeriod){
        Runnable taskSynchronization = doSynchronizationData(this);
        scheduledExecutorService.scheduleAtFixedRate(taskSynchronization, secondsToStart, secondsPeriod, TimeUnit.SECONDS);
    }

    public void synchronizeOnce() {
        Thread thread1 = new Thread(doSynchronizationData(this));
        thread1.start();
    }
    public void stopSynchronization(int seconds) throws InterruptedException {
        scheduledExecutorService.shutdown();
        scheduledExecutorService.awaitTermination(seconds, TimeUnit.SECONDS);
    }

    public void subscribeEvents(SynchronizeDataEvent listener) {
        this.listeners.add(listener);
    }

    public void unsubscribeEvents(SynchronizeDataEvent listener) {
        this.listeners.remove(listener);
    }


    private static Runnable doSynchronizationData(DataSynchronizationManagerImp parent) {
        return () ->{
            if (!isDoingInference) {
                JsonObject changes = new JsonObject();
                changes.add("uploaded", new JsonObject());
                changes.add("updated", new JsonObject());
                int allChanges = 0;
                // Sincronize from mongo to Cosmos
                for (DataRepository dr : parent.mongoRepositories.stream().filter(mr -> mr.isMaster()).collect(Collectors.toList())) {
                    int itemsChanged = 0;
                    if (parent.mongo.countDocuments(dr.getName()) > 0) { // if not empty
                        JsonArray jData = parent.mongo.getAllItemsInCollection(dr.getName());
                        for (JsonElement jItem : jData) { // For all items in mongo
                            JsonObject joItem = jItem.getAsJsonObject();
                            String _id = joItem.get("_id").getAsJsonObject().get("$oid").getAsString();
                            joItem.remove("_id");
                            parent.cosmos.upsertItem(
                                    parent.cosmosDatabase,
                                    dr.getOuterName(),
                                    ImmutableMap.of(dr.getOuterIdentifier(), jItem.getAsJsonObject().get(dr.getOuterIdentifier()).getAsString()),
                                    jItem.getAsJsonObject()
                            );
                            parent.mongo.deleteItem(dr.getName(), _id);
                            itemsChanged += 1;
                            allChanges += 1;
                        }
                    }
                    changes.get("uploaded").getAsJsonObject().addProperty(dr.getName(), itemsChanged);
                    allChanges += itemsChanged;
                }

                // Sincronize from Cosmos to Mongo
                for (DataRepository dr : parent.cosmosRepositories.stream().filter(mr -> mr.isMaster()).collect(Collectors.toList())) { // Obtengo todos los repositorios maestros
                    int itemsChanged = 0;
                    JsonObject jResult = new JsonObject();
                    JsonArray jCosmosData = parent.cosmos.getAllItemsByFilter(parent.cosmosDatabase, dr.getName(), null); // Get all Item in Cosmos



                    for (JsonElement jCosmosItem : jCosmosData) { // For all items in cosmos
                        JsonObject joCosmosItem = jCosmosItem.getAsJsonObject();
                        joCosmosItem.remove("_id");
                         // Get all Item in Cosmos


                        if (dr.getSynchronizablesItems().contains(joCosmosItem.get(dr.getInnerIdentifier()).getAsString()) || joCosmosItem.get(dr.getInnerIdentifier()).getAsString().equals(parent.idDevice)) {

                            JsonArray jMongoData = parent.mongo.getItemsInCollectionWithFilter(dr.getOuterName(), ImmutableMap.of(dr.getOuterIdentifier(), joCosmosItem.get(dr.getInnerIdentifier()).getAsString()));
                            if (jMongoData.size()>0) {
                                for (JsonElement jMongoItem : jMongoData) {
                                    JsonObject joMongoItem = jMongoItem.getAsJsonObject();

                                    boolean equalJson = Utilities.isTwoJsonEquals(joCosmosItem.deepCopy(), joMongoItem.deepCopy(), JSONCompareMode.LENIENT);
                                    if (!equalJson) {
                                        JsonObject jMerged = Utilities.merge(joMongoItem.deepCopy(), joCosmosItem.deepCopy());
                                        parent.mongo.upsertItem(dr.getName(), ImmutableMap.of(dr.getOuterIdentifier(), joCosmosItem.get("id").getAsString()), jMerged);
                                        itemsChanged += 1;
                                        allChanges += 1;
                                    }

                                }
                            } else {
                                parent.mongo.upsertItem(dr.getName(), ImmutableMap.of(dr.getOuterIdentifier(), joCosmosItem.get("id").getAsString()), joCosmosItem);
                                itemsChanged += 1;
                                allChanges += 1;
                            }
                        } else if (dr.getSynchronizablesItems().contains("any")){ // Si debo sincronizar todos los items
                            joCosmosItem.remove("_id");
                            for (Map.Entry<String, JsonElement> entry: joCosmosItem.deepCopy().entrySet()) {
                                if (entry.getKey().startsWith("_"))
                                    joCosmosItem.remove(entry.getKey());
                            }
                            Map<String,Object> filter = ImmutableMap.of(dr.getOuterIdentifier(), joCosmosItem.get(dr.getInnerIdentifier()).getAsInt());
                            JsonArray jMongoItems = parent.mongo.getItemsInCollectionWithFilter(dr.getOuterName(), filter);
                            boolean found = false;
                            for (JsonElement jMongoItem : jMongoItems) {
                                JsonObject joMongoItem = jMongoItem.getAsJsonObject();

                                boolean equalJson = Utilities.isTwoJsonEquals(joCosmosItem.deepCopy(), joMongoItem.deepCopy(), JSONCompareMode.LENIENT);
                                if (!equalJson) {
                                    JsonObject jMerged = Utilities.merge(joMongoItem.deepCopy(), joCosmosItem.deepCopy());
                                    parent.mongo.upsertItem(dr.getName(), ImmutableMap.of(dr.getOuterIdentifier(), joCosmosItem.get("id").getAsString()), jMerged);
                                    itemsChanged += 1;
                                    allChanges += 1;
                                    found = true;
                                    break;
                                } else {
                                    found = true;
                                }

                            }
                            if (!found) {
                                parent.mongo.upsertItem(dr.getOuterName(), filter, joCosmosItem);
                                itemsChanged += 1;
                                allChanges += 1;
                            }
                        }
                    }
                    changes.get("updated").getAsJsonObject().addProperty(dr.getName(), itemsChanged);
                }
                changes.addProperty("allChanges", allChanges);
                for (SynchronizeDataEvent l : parent.listeners) {
                    l.OnDataSynchronizedIsDone(changes);
                }
                parent.isSynchronized = true;
                logger.log(Level.INFO,changes.toString());
            }
        };
    }

    public class DoSynchronizationData implements Runnable {

        public JsonObject response;

        public  DataSynchronizationManagerImp  parent;

        public Runnable init(DataSynchronizationManagerImp parent) {
            this.parent=parent;
            return(this);
        }

        @Override
        public void run() {
            JsonObject changes = new JsonObject();
            changes.add("uploaded",new JsonObject());
            changes.add("updated",new JsonObject());
            int allChanges = 0;
            // Sincronize from mongo to Cosmos
            for (DataRepository dr : parent.mongoRepositories.stream().filter(mr -> mr.isMaster()).collect(Collectors.toList())) {
                int itemsChanged = 0;
                if (mongo.countDocuments(dr.getName())>0) { // if not empty
                    JsonArray jData = mongo.getAllItemsInCollection(dr.getName());
                    for (JsonElement jItem : jData) { // For all items in mongo
                        JsonObject joItem = jItem.getAsJsonObject();
                        String _id = joItem.get("_id").getAsJsonObject().get("$oid").getAsString();
                        joItem.remove("_id");
                        cosmos.upsertItem(
                                cosmosDatabase,
                                dr.getOuterName(),
                                ImmutableMap.of(dr.getOuterIdentifier(), jItem.getAsJsonObject().get(dr.getOuterIdentifier()).getAsString()),
                                jItem.getAsJsonObject()
                        );
                        mongo.deleteItem(dr.getName(),_id);
                        itemsChanged += 1;
                        allChanges += 1;
                    }
                }
                changes.get("uploaded").getAsJsonObject().addProperty(dr.getName(), itemsChanged);
                allChanges += itemsChanged;
            }

            // Sincronize from mongo to Cosmos
            for (DataRepository dr : parent.cosmosRepositories.stream().filter(mr -> mr.isMaster()).collect(Collectors.toList())) {
                int itemsChanged = 0;
                JsonObject jResult = new JsonObject();
                JsonArray jCosmosData = cosmos.getAllItemsByFilter(cosmosDatabase,dr.getName(),null); // Get all Item in Cosmos
                for (JsonElement jCosmosItem : jCosmosData) { // For all items in mongo
                    JsonObject joCosmosItem = jCosmosItem.getAsJsonObject();
                    joCosmosItem.remove("_id");
                    if (dr.getSynchronizablesItems().contains(joCosmosItem.get("id").getAsString()) || joCosmosItem.get("id").getAsString().equals(idDevice)) {

                        JsonArray jMongoData = mongo.getItemsInCollectionWithFilter(dr.getOuterName(),ImmutableMap.of(dr.getOuterIdentifier(),joCosmosItem.get("id").getAsString()));

                        for (JsonElement jMongoItem : jMongoData) {
                            JsonObject joMongoItem = jMongoItem.getAsJsonObject();

                            boolean equalJson = Utilities.isTwoJsonEquals(joCosmosItem.deepCopy(),joMongoItem.deepCopy(), JSONCompareMode.LENIENT);
                            if (!equalJson) {
                                JsonObject jMerged = Utilities.merge(joMongoItem.deepCopy(),joCosmosItem.deepCopy());
                                mongo.upsertItem(dr.getName(),ImmutableMap.of(dr.getOuterIdentifier(),joCosmosItem.get("id").getAsString()), jMerged);
                                itemsChanged += 1;
                                allChanges += 1;
                            }

                        }
                    }
                }
                changes.get("updated").getAsJsonObject().addProperty(dr.getName(), itemsChanged);
            }
            changes.addProperty("allChanges",allChanges);
            logger.log(Level.INFO,changes.toString());
            response = changes;
        }
    }

    public boolean isSynchronized() {
        return isSynchronized;
    }
}
